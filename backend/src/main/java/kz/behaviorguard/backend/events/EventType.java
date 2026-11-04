package kz.behaviorguard.backend.events;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Разрешённые типы событий и их поля. Всё, что не описано здесь, сервер отклоняет.
 * Это серверная часть политики приватности: SDK не может передать лишние поля.
 * Список должен совпадать с sdk/src/events.ts.
 */
public enum EventType {

    /** Касание: длительность нажатия и интервал от предыдущего касания. */
    TAP(
            req("durationMs", FieldRule.number(10, 10_000)),
            opt("intervalMs", FieldRule.number(0, 600_000))),

    /** Свайп: расстояние, длительность, скорость, направление. */
    SWIPE(
            req("distancePx", FieldRule.number(0, 20_000)),
            req("durationMs", FieldRule.number(1, 10_000)),
            req("avgSpeedPxS", FieldRule.number(0, 20_000)),
            req("direction", FieldRule.oneOf("UP", "DOWN", "LEFT", "RIGHT"))),

    /** Траектория касания внутри одного жеста: число точек, прямолинейность, скорость. */
    TOUCH_TRAJECTORY(
            req("pointCount", FieldRule.number(1, 100_000)),
            req("straightness", FieldRule.number(0, 1)),
            req("avgSpeedPxS", FieldRule.number(0, 20_000))),

    /** Прокрутка списка или экрана. */
    SCROLL(
            req("distancePx", FieldRule.number(0, 100_000)),
            req("durationMs", FieldRule.number(1, 60_000)),
            req("avgSpeedPxS", FieldRule.number(0, 20_000)),
            req("direction", FieldRule.oneOf("UP", "DOWN", "LEFT", "RIGHT"))),

    /** Агрегат набора текста в поле без содержимого: только тайминги и счётчики. */
    TEXT_INPUT(
            req("fieldId", FieldRule.text("^[a-z0-9._-]{1,64}$")),
            req("keyCount", FieldRule.number(0, 100_000)),
            req("keyIntervalAvgMs", FieldRule.number(0, 10_000)),
            req("keyIntervalStdMs", FieldRule.number(0, 10_000)),
            req("charsPerMinute", FieldRule.number(0, 2_000)),
            req("backspaceCount", FieldRule.number(0, 100_000))),

    /** Факт вставки текста в поле. Сам текст не передаётся. */
    PASTE(
            req("fieldId", FieldRule.text("^[a-z0-9._-]{1,64}$")),
            req("pasteCount", FieldRule.number(1, 1_000))),

    /** Экран, на котором пользователь находился, и время на нём. */
    SCREEN_VIEW(
            req("screen", FieldRule.text("^[a-z0-9._-]{1,64}$")),
            req("dwellMs", FieldRule.number(0, 86_400_000))),

    /** Бездействие. */
    IDLE(
            req("durationMs", FieldRule.number(0, 86_400_000))),

    /** Время заполнения формы. */
    FORM_COMPLETION(
            req("formId", FieldRule.text("^[a-z0-9._-]{1,64}$")),
            req("durationMs", FieldRule.number(0, 86_400_000)),
            req("fieldCount", FieldRule.number(1, 500)));

    private final Map<String, Field> fields;

    EventType(Field... fields) {
        Map<String, Field> map = new LinkedHashMap<>();
        for (Field f : fields) {
            map.put(f.name(), f);
        }
        this.fields = Map.copyOf(map);
    }

    /** Возвращает список нарушений; пустой список означает, что payload допустим. */
    public List<String> violations(Map<String, Object> payload) {
        if (payload == null) {
            return List.of("payload обязателен");
        }
        List<String> errors = new java.util.ArrayList<>();
        for (Field f : fields.values()) {
            if (f.required() && !payload.containsKey(f.name())) {
                errors.add("не хватает поля " + f.name());
            }
        }
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            Field f = fields.get(e.getKey());
            if (f == null) {
                errors.add("неизвестное поле " + e.getKey());
                continue;
            }
            String v = f.rule().violation(e.getValue());
            if (v != null) {
                errors.add("поле " + e.getKey() + ": " + v);
            }
        }
        return errors;
    }

    public record Field(String name, FieldRule rule, boolean required) {
    }

    private static Field req(String name, FieldRule rule) {
        return new Field(name, rule, true);
    }

    private static Field opt(String name, FieldRule rule) {
        return new Field(name, rule, false);
    }
}
