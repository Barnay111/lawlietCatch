package kz.behaviorguard.backend.events;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record EventBatchRequest(
        @NotNull UUID sessionId,
        @NotNull @Size(min = 1, max = 200) List<@Valid EventIn> events
) {
}
