package kz.behaviorguard.backend.profile;

import java.util.LinkedHashMap;
import java.util.Map;

/** Онлайн-обновление среднего и дисперсии (алгоритм Уэлфорда). Числово устойчивее суммы квадратов. */
public final class Welford {

    private Welford() {
    }

    public record State(double mean, double m2, long n) {
        double std() {
            return n >= 2 ? Math.sqrt(m2 / (n - 1)) : 0.0;
        }
    }

    public static Map<String, Object> update(State s, double x) {
        long n = s.n() + 1;
        double delta = x - s.mean();
        double mean = s.mean() + delta / n;
        double m2 = s.m2() + delta * (x - mean);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mean", mean);
        out.put("m2", m2);
        out.put("n", n);
        return out;
    }

    /** Для ответа API и для ML-сервиса: mean, std, n. */
    public static Map<String, Object> summary(Map<String, Object> raw) {
        State s = new State(
                ((Number) raw.get("mean")).doubleValue(),
                ((Number) raw.get("m2")).doubleValue(),
                ((Number) raw.get("n")).longValue());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mean", s.mean());
        out.put("std", s.std());
        out.put("n", s.n());
        return out;
    }
}
