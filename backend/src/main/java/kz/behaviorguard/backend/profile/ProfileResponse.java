package kz.behaviorguard.backend.profile;

import kz.behaviorguard.backend.user.AnalyzedUser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Состояние профиля для GET /api/v1/users/{id}/profile. Сырые значения не отдаём: только статистики. */
public record ProfileResponse(
        UUID userId,
        AnalyzedUser.ProfileStatus profileStatus,
        int sessionCount,
        Map<String, Map<String, Object>> features
) {
    public static ProfileResponse of(UUID userId, BehavioralProfile profile) {
        Map<String, Map<String, Object>> features = new LinkedHashMap<>();
        if (profile != null) {
            for (String name : FeatureExtractor.NAMES) {
                Map<String, Object> raw = profile.statsFor(name);
                if (raw != null) features.put(name, Welford.summary(raw));
            }
        }
        int count = profile == null ? 0 : profile.getSessionCount();
        return new ProfileResponse(userId, ProfileService.statusFor(count), count, features);
    }
}
