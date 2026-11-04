package kz.behaviorguard.backend;

import kz.behaviorguard.backend.risk.RiskPolicy;
import kz.behaviorguard.backend.verification.VerificationPolicy;
import kz.behaviorguard.backend.verification.VerificationPolicy.Decision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationPolicyTests {

    @Test
    void highRiskRequiresStepUp() {
        var r = VerificationPolicy.decide(RiskPolicy.RiskLevel.HIGH, true, 1_000L, false);
        assertThat(r.decision()).isEqualTo(Decision.STEP_UP);
        assertThat(r.requiresOtp()).isTrue();
        assertThat(r.reasons()).containsExactly("HIGH_RISK");
    }

    @Test
    void insufficientDataNeverPassesSilently() {
        var unknown = VerificationPolicy.decide(RiskPolicy.RiskLevel.UNKNOWN, false, 1_000L, false);
        assertThat(unknown.decision()).isEqualTo(Decision.CONFIRM);
        assertThat(unknown.reasons()).containsExactly("INSUFFICIENT_DATA");
        // Профиль не готов, даже если уровень формально LOW: данных мало, подтверждаем.
        var notReady = VerificationPolicy.decide(RiskPolicy.RiskLevel.LOW, false, 1_000L, false);
        assertThat(notReady.decision()).isEqualTo(Decision.CONFIRM);
    }

    @Test
    void mediumRiskRequiresConfirmation() {
        var r = VerificationPolicy.decide(RiskPolicy.RiskLevel.MEDIUM, true, 500L, false);
        assertThat(r.decision()).isEqualTo(Decision.CONFIRM);
        assertThat(r.reasons()).containsExactly("MEDIUM_RISK");
    }

    @Test
    void lowRiskSmallAmountIsAllowed() {
        var r = VerificationPolicy.decide(RiskPolicy.RiskLevel.LOW, true, 5_000L, false);
        assertThat(r.decision()).isEqualTo(Decision.ALLOW);
        assertThat(r.requiresOtp()).isFalse();
    }

    @Test
    void lowRiskLargeAmountRequiresConfirmation() {
        var r = VerificationPolicy.decide(RiskPolicy.RiskLevel.LOW, true, VerificationPolicy.CONFIRM_AMOUNT, false);
        assertThat(r.decision()).isEqualTo(Decision.CONFIRM);
        assertThat(r.reasons()).containsExactly("LARGE_AMOUNT");
    }

    @Test
    void newRecipientWithMediumAmountRequiresConfirmation() {
        var r = VerificationPolicy.decide(RiskPolicy.RiskLevel.LOW, true, VerificationPolicy.NEW_RECIPIENT_CONFIRM_AMOUNT, true);
        assertThat(r.decision()).isEqualTo(Decision.CONFIRM);
        assertThat(r.reasons()).containsExactly("NEW_RECIPIENT");
    }

    @Test
    void missingAmountIsTreatedAsZero() {
        var r = VerificationPolicy.decide(RiskPolicy.RiskLevel.LOW, true, null, null);
        assertThat(r.decision()).isEqualTo(Decision.ALLOW);
    }
}
