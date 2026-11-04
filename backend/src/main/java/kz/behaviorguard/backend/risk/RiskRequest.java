package kz.behaviorguard.backend.risk;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RiskRequest(@NotNull UUID sessionId, @Valid Context context) {

    /** Контекст операции. Используется для аудита и будущих правил (например, новый получатель). */
    public record Context(
            @NotBlank
            @Size(max = 40)
            @Pattern(regexp = "^[A-Z_]+$", message = "action: заглавные буквы и _")
            String action,
            Boolean newRecipient
    ) {
    }
}
