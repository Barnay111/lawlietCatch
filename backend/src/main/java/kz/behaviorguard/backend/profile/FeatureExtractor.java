package kz.behaviorguard.backend.profile;

import kz.behaviorguard.backend.events.BehavioralEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Признаки сессии из событий. Имена и порядок совпадают с ml-engine/app/features.py.
 * Признак без данных — null: ML-сервис его не учитывает, а не считает нормальным.
 */
public final class FeatureExtractor {

    public static final List<String> NAMES = List.of(
            "tap_duration_ms",
            "tap_interval_ms",
            "swipe_speed_px_s",
            "scroll_speed_px_s",
            "key_interval_ms",
            "chars_per_minute",
            "backspace_ratio",
            "screen_dwell_ms");

    /** Минимум набранных символов, чтобы доля Backspace была осмысленной. */
    static final int MIN_KEYS_FOR_RATIO = 10;

    private FeatureExtractor() {
    }

    public static Map<String, Double> extract(List<BehavioralEvent> events) {
        List<Double> tapDuration = new ArrayList<>();
        List<Double> tapInterval = new ArrayList<>();
        List<Double> swipeSpeed = new ArrayList<>();
        List<Double> scrollSpeed = new ArrayList<>();
        List<Double> dwell = new ArrayList<>();
        // Для текста средние взвешиваются числом нажатий: длинные поля весят больше коротких.
        double keyIntervalWeighted = 0, cpmWeighted = 0, keyWeightSum = 0;
        double backspaces = 0, keys = 0;

        for (BehavioralEvent e : events) {
            Map<String, Object> p = e.getPayload();
            switch (e.getEventType()) {
                case TAP -> {
                    add(tapDuration, p, "durationMs");
                    add(tapInterval, p, "intervalMs");
                }
                case SWIPE -> add(swipeSpeed, p, "avgSpeedPxS");
                case SCROLL -> add(scrollSpeed, p, "avgSpeedPxS");
                case SCREEN_VIEW -> add(dwell, p, "dwellMs");
                case TEXT_INPUT -> {
                    Double kc = num(p, "keyCount");
                    if (kc != null && kc > 0) {
                        Double ki = num(p, "keyIntervalAvgMs");
                        Double cpm = num(p, "charsPerMinute");
                        if (ki != null) keyIntervalWeighted += ki * kc;
                        if (cpm != null) cpmWeighted += cpm * kc;
                        keyWeightSum += kc;
                        keys += kc;
                    }
                    Double bs = num(p, "backspaceCount");
                    if (bs != null) backspaces += bs;
                }
                default -> {
                    // PASTE, IDLE, TOUCH_TRAJECTORY, FORM_COMPLETION в признаки MVP не входят
                }
            }
        }

        Map<String, Double> out = new LinkedHashMap<>();
        out.put("tap_duration_ms", mean(tapDuration));
        out.put("tap_interval_ms", mean(tapInterval));
        out.put("swipe_speed_px_s", mean(swipeSpeed));
        out.put("scroll_speed_px_s", mean(scrollSpeed));
        out.put("key_interval_ms", keyWeightSum > 0 ? keyIntervalWeighted / keyWeightSum : null);
        out.put("chars_per_minute", keyWeightSum > 0 ? cpmWeighted / keyWeightSum : null);
        out.put("backspace_ratio", keys >= MIN_KEYS_FOR_RATIO ? backspaces / keys : null);
        out.put("screen_dwell_ms", mean(dwell));
        return out;
    }

    /** Сколько признаков реально посчитано. */
    public static long available(Map<String, Double> features) {
        return features.values().stream().filter(v -> v != null).count();
    }

    private static void add(List<Double> into, Map<String, Object> payload, String key) {
        Double v = num(payload, key);
        if (v != null) into.add(v);
    }

    private static Double num(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v instanceof Number n ? n.doubleValue() : null;
    }

    private static Double mean(List<Double> values) {
        if (values.isEmpty()) return null;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.size();
    }
}
