package kz.behaviorguard.backend.session;

import jakarta.validation.Valid;
import kz.behaviorguard.backend.security.ApiKeyPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(@AuthenticationPrincipal ApiKeyPrincipal principal,
                                  @Valid @RequestBody CreateSessionRequest request) {
        return sessionService.create(principal.organizationId(), request);
    }

    @PostMapping("/{id}/finish")
    public SessionResponse finish(@AuthenticationPrincipal ApiKeyPrincipal principal,
                                  @PathVariable java.util.UUID id) {
        return sessionService.finish(principal.organizationId(), id);
    }
}
