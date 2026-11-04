package kz.behaviorguard.backend.risk;

/**
 * Пороги и рекомендации. Решение принимает банк: сервис только даёт рекомендацию и никого не блокирует.
 * Значения согласуются с пилотным клиентом (раздел 10 ТЗ).
 */
public final class RiskPolicy {

    public static final int MEDIUM_FROM = 40;
    public static final int HIGH_FROM = 70;

    private RiskPolicy() {
    }

    public static RiskLevel levelFor(int score) {
        if (score >= HIGH_FROM) return RiskLevel.HIGH;
        if (score >= MEDIUM_FROM) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    public static Recommendation recommendationFor(RiskLevel level) {
        return switch (level) {
            case HIGH -> Recommendation.STEP_UP_AUTHENTICATION;
            case MEDIUM -> Recommendation.MONITOR;
            case LOW -> Recommendation.NONE;
            case UNKNOWN -> Recommendation.FALLBACK_TO_RULES;
        };
    }

    public enum RiskLevel { LOW, MEDIUM, HIGH, UNKNOWN }

    /** FALLBACK_TO_RULES: данных недостаточно, банк применяет собственные правила. */
    public enum Recommendation { NONE, MONITOR, STEP_UP_AUTHENTICATION, FALLBACK_TO_RULES }
}
