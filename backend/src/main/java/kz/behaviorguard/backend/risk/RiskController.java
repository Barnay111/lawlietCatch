package kz.behaviorguard.backend.risk;

import jakarta.validation.Valid;
import kz.behaviorguard.backend.security.ApiKeyPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Вызывается бэкендом банка (ключ SERVER), не SDK. */
@RestController
@RequestMapping("/api/v1/risk")
public class RiskController {

    private final RiskService risk;

    public RiskController(RiskService risk) {
        this.risk = risk;
    }

    @PostMapping("/assess")
    @ResponseStatus(HttpStatus.OK)
    public RiskResponse assess(@AuthenticationPrincipal ApiKeyPrincipal principal,
                               @Valid @RequestBody RiskRequest request) {
        return risk.assess(principal.organizationId(), request.sessionId(), request.context());
    }

    @GetMapping("/{sessionId}")
    public RiskResponse latest(@AuthenticationPrincipal ApiKeyPrincipal principal,
                               @PathVariable UUID sessionId) {
        return risk.latest(principal.organizationId(), sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Оценка не найдена"));
    }

    @GetMapping("/{sessionId}/history")
    public List<RiskResponse> history(@AuthenticationPrincipal ApiKeyPrincipal principal,
                                      @PathVariable UUID sessionId) {
        return risk.history(principal.organizationId(), sessionId);
    }
}
