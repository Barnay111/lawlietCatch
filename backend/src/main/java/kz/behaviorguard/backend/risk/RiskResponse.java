package kz.behaviorguard.backend.risk;

import java.util.List;
import java.util.UUID;

/** Формат из раздела 5 ТЗ. riskScore = null, если риск UNKNOWN. */
public record RiskResponse(
        UUID sessionId,
        Integer riskScore,
        RiskPolicy.RiskLevel riskLevel,
        String profileStatus,
        List<String> reasons,
        RiskPolicy.Recommendation recommendation,
        String modelVersion
) {
}
