package kz.behaviorguard.backend.risk;

import kz.behaviorguard.backend.events.BehavioralEventRepository;
import kz.behaviorguard.backend.profile.BehavioralProfile;
import kz.behaviorguard.backend.profile.FeatureExtractor;
import kz.behaviorguard.backend.profile.ProfileService;
import kz.behaviorguard.backend.session.UserSession;
import kz.behaviorguard.backend.session.UserSessionRepository;
import kz.behaviorguard.backend.user.AnalyzedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RiskService {

    /** Меньше признаков — сравнивать не с чем: UNKNOWN. Совпадает с MIN_COVERAGE в ml-engine (0.5 от 8). */
    static final int MIN_FEATURES = 4;

    private final UserSessionRepository sessions;
    private final BehavioralEventRepository events;
    private final ProfileService profiles;
    private final RiskModelClient model;
    private final RiskAssessmentRepository assessments;

    public RiskService(UserSessionRepository sessions,
                       BehavioralEventRepository events,
                       ProfileService profiles,
                       RiskModelClient model,
                       RiskAssessmentRepository assessments) {
        this.sessions = sessions;
        this.events = events;
        this.profiles = profiles;
        this.model = model;
        this.assessments = assessments;
    }

    @Transactional
    public RiskResponse assess(UUID organizationId, UUID sessionId, RiskRequest.Context context) {
        UserSession session = sessions.findByIdAndOrganizationId(sessionId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена"));
        AnalyzedUser user = session.getUser();
        Optional<BehavioralProfile> profile = profiles.find(organizationId, user.getId());
        int sessionCount = profile.map(BehavioralProfile::getSessionCount).orElse(0);
        Map<String, Double> features = FeatureExtractor.extract(
                events.findBySessionIdAndOrganizationId(sessionId, organizationId));
        Map<String, Object> details = detailsOf(context, features, sessionCount);

        // Без готового профиля и достаточного числа признаков риск не оцениваем.
        if (sessionCount < ProfileService.READY_MIN_SESSIONS) {
            return unknown(session, user, List.of("INSUFFICIENT_PROFILE"), details);
        }
        if (FeatureExtractor.available(features) < MIN_FEATURES) {
            return unknown(session, user, List.of("INSUFFICIENT_EVENTS"), details);
        }

        MlScore score;
        try {
            score = model.score(features, profile.orElseThrow());
        } catch (ModelInsufficientDataException e) {
            return unknown(session, user, List.of("INSUFFICIENT_EVENTS"), details);
        } catch (ModelUnavailableException e) {
            // Не выдаём низкий риск, когда модель недоступна: это было бы ложной уверенностью.
            return unknown(session, user, List.of("MODEL_UNAVAILABLE"), details);
        }

        details.put("anomalyScore", score.anomalyScore());
        details.put("profileScore", score.profileScore());
        RiskPolicy.RiskLevel level = RiskPolicy.levelFor(score.riskScore());
        RiskPolicy.Recommendation rec = RiskPolicy.recommendationFor(level);
        RiskAssessment saved = assessments.save(new RiskAssessment(
                session.getOrganization(), session, score.riskScore(), level, rec,
                score.reasons(), details, score.modelVersion()));
        session.recordAssessment(level, saved.getCreatedAt());
        return toResponse(saved, user.getProfileStatus());
    }

    @Transactional(readOnly = true)
    public Optional<RiskResponse> latest(UUID organizationId, UUID sessionId) {
        return assessments.findFirstBySessionIdAndOrganizationIdOrderByCreatedAtDesc(sessionId, organizationId)
                .map(a -> toResponse(a, a.getSession().getUser().getProfileStatus()));
    }

    /** История оценок по сессии, от новых к старым. */
    @Transactional(readOnly = true)
    public List<RiskResponse> history(UUID organizationId, UUID sessionId) {
        return assessments.findBySessionIdAndOrganizationIdOrderByCreatedAtDesc(sessionId, organizationId).stream()
                .map(a -> toResponse(a, a.getSession().getUser().getProfileStatus()))
                .toList();
    }

    private RiskResponse unknown(UserSession session, AnalyzedUser user, List<String> reasons, Map<String, Object> details) {
        RiskAssessment saved = assessments.save(new RiskAssessment(
                session.getOrganization(), session, null, RiskPolicy.RiskLevel.UNKNOWN,
                RiskPolicy.Recommendation.FALLBACK_TO_RULES, reasons, details, "n/a"));
        session.recordAssessment(RiskPolicy.RiskLevel.UNKNOWN, saved.getCreatedAt());
        return toResponse(saved, user.getProfileStatus());
    }

    private static Map<String, Object> detailsOf(RiskRequest.Context ctx, Map<String, Double> features, int sessionCount) {
        Map<String, Object> d = new LinkedHashMap<>();
        if (ctx != null) {
            d.put("action", ctx.action());
            d.put("newRecipient", ctx.newRecipient());
        }
        d.put("sessionCount", sessionCount);
        d.put("featuresAvailable", FeatureExtractor.available(features));
        return d;
    }

    private static RiskResponse toResponse(RiskAssessment a, AnalyzedUser.ProfileStatus status) {
        return new RiskResponse(
                a.getSession().getId(),
                a.getRiskScore() == null ? null : a.getRiskScore().intValue(),
                a.getRiskLevel(),
                status.name(),
                a.getReasons(),
                a.getRecommendation(),
                a.getModelVersion());
    }
}
