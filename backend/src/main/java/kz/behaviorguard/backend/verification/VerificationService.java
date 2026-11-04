package kz.behaviorguard.backend.verification;

import kz.behaviorguard.backend.audit.AuditService;
import kz.behaviorguard.backend.profile.ProfileService;
import kz.behaviorguard.backend.risk.RiskAssessment;
import kz.behaviorguard.backend.risk.RiskAssessmentRepository;
import kz.behaviorguard.backend.risk.RiskPolicy;
import kz.behaviorguard.backend.risk.RiskRequest;
import kz.behaviorguard.backend.risk.RiskService;
import kz.behaviorguard.backend.session.UserSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Service
public class VerificationService {

    private final RiskService risk;
    private final UserSessionRepository sessions;
    private final ProfileService profiles;
    private final AuditService audit;

    public VerificationService(RiskService risk, UserSessionRepository sessions,
                               ProfileService profiles, AuditService audit) {
        this.risk = risk;
        this.sessions = sessions;
        this.profiles = profiles;
        this.audit = audit;
    }

    /**
     * Проверяет, похожа ли операция на поведение клиента, и возвращает решение банку.
     * Оценка риска берётся через RiskService; решение — через VerificationPolicy.
     */
    @Transactional
    public VerificationPolicy.Result decide(UUID organizationId, RiskRequest request) {
        var session = sessions.findByIdAndOrganizationId(request.sessionId(), organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена"));
        var assessment = risk.assess(organizationId, request.sessionId(), request.context());
        boolean profileReady = profiles.find(organizationId, session.getUser().getId())
                .map(p -> p.getSessionCount() >= ProfileService.READY_MIN_SESSIONS)
                .orElse(false);

        Long amount = request.context() == null ? null : request.context().amount();
        Boolean newRecipient = request.context() == null ? null : request.context().newRecipient();
        RiskPolicy.RiskLevel level = assessment.riskLevel();
        VerificationPolicy.Result result = VerificationPolicy.decide(level, profileReady, amount, newRecipient);

        // Решение пишем в аудит без персональных данных: только уровень, решение и причины.
        audit.record(organizationId, "SYSTEM", "verification", "VERIFICATION_DECIDED",
                request.sessionId().toString(), Map.of(
                        "decision", result.decision().name(),
                        "riskLevel", level.name(),
                        "reasons", result.reasons()));
        return result;
    }
}
