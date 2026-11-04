package kz.behaviorguard.backend.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * userId — только псевдоним из системы клиента. Ограничение символов не даёт
 * передать email или телефон: символы @ и + сюда не проходят.
 */
public record CreateSessionRequest(
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "userId: только буквы, цифры и . _ : -")
        String userId,

        @NotNull UserSession.DeviceType deviceType,

        @NotBlank
        @Size(max = 20)
        @Pattern(regexp = "^[0-9A-Za-z.-]{1,20}$", message = "sdkVersion: только буквы, цифры, точка и дефис")
        String sdkVersion
) {
}
