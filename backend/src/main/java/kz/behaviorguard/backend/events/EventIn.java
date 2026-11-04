package kz.behaviorguard.backend.events;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record EventIn(
        @NotNull EventType eventType,
        @NotNull Instant eventTime,
        @NotNull Map<String, Object> payload
) {
}
