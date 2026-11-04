package kz.behaviorguard.backend.erasure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kz.behaviorguard.backend.admin.AdminPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Только роль ADMIN: удаление данных клиента по установленной процедуре. */
@RestController
@RequestMapping("/api/v1/admin/erasure")
public class ErasureController {

    public record ErasureRequest(@NotBlank @Size(max = 128) String userId,
                                 @NotBlank @Size(min = 10, max = 300) String reason) {
    }

    private final ErasureService erasure;

    public ErasureController(ErasureService erasure) {
        this.erasure = erasure;
    }

    @PostMapping
    public ErasureService.Result erase(@AuthenticationPrincipal AdminPrincipal actor,
                                       @Valid @RequestBody ErasureRequest req) {
        ErasureService.Result r = erasure.erase(actor.organizationId(), actor.adminId(), req.userId(), req.reason());
        if (r == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
        }
        return r;
    }
}
