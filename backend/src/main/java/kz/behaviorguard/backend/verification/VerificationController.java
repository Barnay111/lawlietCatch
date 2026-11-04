package kz.behaviorguard.backend.verification;

import jakarta.validation.Valid;
import kz.behaviorguard.backend.risk.RiskRequest;
import kz.behaviorguard.backend.security.ApiKeyPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Вызывает бэкенд банка с серверным ключом. Ответ — рекомендация: решение о проведении принимает банк. */
@RestController
@RequestMapping("/api/v1/verification")
public class VerificationController {

    public record DecisionResponse(VerificationPolicy.Decision decision, boolean requiresOtp, List<String> reasons) {
    }

    private final VerificationService verification;

    public VerificationController(VerificationService verification) {
        this.verification = verification;
    }

    @PostMapping("/decide")
    public DecisionResponse decide(@AuthenticationPrincipal ApiKeyPrincipal principal,
                                   @Valid @RequestBody RiskRequest request) {
        var r = verification.decide(principal.organizationId(), request);
        return new DecisionResponse(r.decision(), r.requiresOtp(), r.reasons());
    }
}
