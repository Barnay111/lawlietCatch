package kz.behaviorguard.backend.session;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String userId,
        UserSession.Status status,
        Instant startedAt,
        UserSession.DeviceType deviceType,
        String sdkVersion
) {
    static SessionResponse from(UserSession s) {
        return new SessionResponse(s.getId(), s.getUser().getExternalUserId(), s.getStatus(),
                s.getStartedAt(), s.getDeviceType(), s.getSdkVersion());
    }
}
