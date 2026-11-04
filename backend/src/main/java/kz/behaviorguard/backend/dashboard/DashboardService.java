package kz.behaviorguard.backend.dashboard;

import kz.behaviorguard.backend.audit.AuditService;
import kz.behaviorguard.backend.events.BehavioralEvent;
import kz.behaviorguard.backend.events.BehavioralEventRepository;
import kz.behaviorguard.backend.profile.FeatureExtractor;
import kz.behaviorguard.backend.profile.ProfileResponse;
import kz.behaviorguard.backend.profile.ProfileService;
import kz.behaviorguard.backend.admin.AdminPrincipal;
import kz.behaviorguard.backend.risk.RiskAssessment;
import kz.behaviorguard.backend.risk.RiskAssessmentRepository;
import kz.behaviorguard.backend.risk.RiskPolicy;
import kz.behaviorguard.backend.session.UserSession;
import kz.behaviorguard.backend.session.UserSessionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static kz.behaviorguard.backend.dashboard.DashboardDtos.*;

@Service
public class DashboardService {

    private static final int RECENT_LIMIT = 20;

    private final UserSessionRepository sessions;
    private final RiskAssessmentRepository assessments;
    private final BehavioralEventRepository events;
    private final ProfileService profiles;
    private final AuditService audit;

    public DashboardService(UserSessionRepository sessions, RiskAssessmentRepository assessments,
                            BehavioralEventRepository events, ProfileService profiles, AuditService audit) {
        this.sessions = sessions;
        this.assessments = assessments;
        this.events = events;
        this.profiles = profiles;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID orgId, int hours) {
        Instant to = Instant.now();
        Instant from = to.minus(hours, ChronoUnit.HOURS);

        // Все агрегаты считает PostgreSQL: объём оценок не загружается в память приложения.
        Map<String, Long> levels = new LinkedHashMap<>();
        for (RiskPolicy.RiskLevel l : RiskPolicy.RiskLevel.values()) {
            levels.put(l.name(), 0L);
        }
        long assessed = 0;
        for (var row : sessions.countByLatestRisk(orgId, from, to)) {
            levels.merge(row.getLevel().name(), row.getCnt(), Long::sum);
            assessed += row.getCnt();
        }

        long started = sessions.countByOrganizationIdAndStartedAtBetween(orgId, from, to);
        List<TimelineBucket> timeline = timeline(assessments.countByHour(orgId, from, to));
        List<AssessmentRow> recent = assessments
                .findRecentForOverview(orgId, from, to, PageRequest.of(0, RECENT_LIMIT))
                .stream().map(DashboardService::row).toList();

        return new Overview(
                from.toString(), to.toString(), started, assessed, levels,
                levels.get("MEDIUM") + levels.get("HIGH"),
                levels.get("HIGH"),
                levels.get("UNKNOWN"),
                timeline, recent);
    }

    @Transactional(readOnly = true)
    public Page<AssessmentRow> feed(UUID orgId, RiskPolicy.RiskLevel level, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        var result = assessments.feed(orgId, level, PageRequest.of(Math.max(page, 0), safeSize));
        return new Page<>(result.map(DashboardService::row).getContent(), page, safeSize, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public SessionDetail sessionDetail(AdminPrincipal actor, UUID sessionId) {
        UserSession s = sessions.findByIdAndOrganizationId(sessionId, actor.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена"));
        List<BehavioralEvent> evs = events.findBySessionIdAndOrganizationId(sessionId, actor.organizationId());

        Map<String, Long> types = evs.stream()
                .collect(Collectors.groupingBy(e -> e.getEventType().name(), LinkedHashMap::new, Collectors.counting()));
        Map<String, Double> features = new LinkedHashMap<>(FeatureExtractor.extract(evs));
        List<AssessmentRow> history = assessments
                .findBySessionIdAndOrganizationIdOrderByCreatedAtDesc(sessionId, actor.organizationId())
                .stream().map(DashboardService::row).toList();

        audit.record(actor.organizationId(), "ADMIN", actor.adminId().toString(),
                "SESSION_VIEW", sessionId.toString(), Map.of("eventCount", evs.size()));

        return new SessionDetail(
                s.getId().toString(),
                s.getUser().getExternalUserId(),
                s.getStartedAt().toString(),
                s.getEndedAt() == null ? null : s.getEndedAt().toString(),
                s.getStatus().name(),
                s.getDeviceType().name(),
                s.getSdkVersion(),
                s.getEventCount(),
                types,
                features,
                history.isEmpty() ? null : history.get(0),
                history);
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile(AdminPrincipal actor, UUID userId) {
        var user = profiles.findUser(actor.organizationId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
        audit.record(actor.organizationId(), "ADMIN", actor.adminId().toString(),
                "PROFILE_VIEW", userId.toString(), Map.of());
        return ProfileResponse.of(user.getId(), profiles.find(actor.organizationId(), user.getId()).orElse(null));
    }

    /** Из плоских строк (час, уровень, число) собирает ряд с нулями для всех уровней. */
    private static List<TimelineBucket> timeline(List<RiskAssessmentRepository.HourCount> rows) {
        Map<String, Map<String, Long>> byHour = new LinkedHashMap<>();
        for (var r : rows) {
            Map<String, Long> counts = byHour.computeIfAbsent(r.getHour(), h -> {
                Map<String, Long> zero = new LinkedHashMap<>();
                for (RiskPolicy.RiskLevel l : RiskPolicy.RiskLevel.values()) zero.put(l.name(), 0L);
                return zero;
            });
            counts.merge(r.getLevel(), r.getCnt(), Long::sum);
        }
        List<TimelineBucket> out = new ArrayList<>();
        byHour.forEach((h, c) -> out.add(new TimelineBucket(h, c)));
        return out;
    }

    private static AssessmentRow row(RiskAssessment a) {
        return new AssessmentRow(
                a.getCreatedAt().toString(),
                a.getSession().getId().toString(),
                a.getSession().getUser().getExternalUserId(),
                a.getRiskScore() == null ? null : a.getRiskScore().intValue(),
                a.getRiskLevel().name(),
                a.getRecommendation().name(),
                a.getReasons(),
                a.getModelVersion());
    }
}
