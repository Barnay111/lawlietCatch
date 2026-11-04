package kz.behaviorguard.backend.verification;

import kz.behaviorguard.backend.risk.RiskPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * Решение по операции: пропустить, запросить код подтверждения или усиленную проверку.
 * Чистая функция без БД: правила читаются и тестируются отдельно.
 *
 * Принцип: при нехватке данных никогда не пропускаем молча. Недостаток данных — это повод
 * для подтверждения, а не для доверия.
 */
public final class VerificationPolicy {

    /** Сумма, с которой требуется подтверждение даже при низком риске. В тенге. */
    public static final long CONFIRM_AMOUNT = 1_000_000L;
    /** Сумма для нового получателя, с которой требуется подтверждение при низком риске. */
    public static final long NEW_RECIPIENT_CONFIRM_AMOUNT = 100_000L;

    private VerificationPolicy() {
    }

    public enum Decision { ALLOW, CONFIRM, STEP_UP }

    public record Result(Decision decision, boolean requiresOtp, List<String> reasons) {
    }

    public static Result decide(RiskPolicy.RiskLevel level, boolean profileReady, Long amount, Boolean newRecipient) {
        List<String> reasons = new ArrayList<>();
        boolean newRecip = Boolean.TRUE.equals(newRecipient);
        long sum = amount == null ? 0L : amount;

        // 1. Высокий риск — усиленная проверка.
        if (level == RiskPolicy.RiskLevel.HIGH) {
            reasons.add("HIGH_RISK");
            return new Result(Decision.STEP_UP, true, reasons);
        }
        // 2. Недостаточно данных: профиль не готов или признаков мало. Не пропускаем молча.
        if (level == RiskPolicy.RiskLevel.UNKNOWN || !profileReady) {
            reasons.add("INSUFFICIENT_DATA");
            return new Result(Decision.CONFIRM, true, reasons);
        }
        // 3. Средний риск — подтверждение кодом.
        if (level == RiskPolicy.RiskLevel.MEDIUM) {
            reasons.add("MEDIUM_RISK");
            return new Result(Decision.CONFIRM, true, reasons);
        }
        // 4. Низкий риск, но крупная сумма или новый получатель с заметной суммой.
        if (sum >= CONFIRM_AMOUNT) {
            reasons.add("LARGE_AMOUNT");
            return new Result(Decision.CONFIRM, true, reasons);
        }
        if (newRecip && sum >= NEW_RECIPIENT_CONFIRM_AMOUNT) {
            reasons.add("NEW_RECIPIENT");
            return new Result(Decision.CONFIRM, true, reasons);
        }
        return new Result(Decision.ALLOW, false, reasons);
    }
}
