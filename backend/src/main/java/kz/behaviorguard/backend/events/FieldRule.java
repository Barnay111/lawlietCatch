package kz.behaviorguard.backend.events;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Правило для одного поля payload. Возвращает текст ошибки или null.
 * Текст ошибки НЕ содержит значение поля: в нём может оказаться что-то чувствительное.
 */
public sealed interface FieldRule permits FieldRule.Num, FieldRule.Enum, FieldRule.Text {

    String violation(Object value);

    static FieldRule number(double min, double max) {
        return new Num(min, max);
    }

    static FieldRule oneOf(String... values) {
        return new Enum(Set.of(values));
    }

    static FieldRule text(String regex) {
        return new Text(Pattern.compile(regex));
    }

    record Num(double min, double max) implements FieldRule {
        @Override
        public String violation(Object value) {
            if (!(value instanceof Number n)) {
                return "ожидается число";
            }
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d < min || d > max) {
                return "значение вне допустимого диапазона";
            }
            return null;
        }
    }

    record Enum(Set<String> values) implements FieldRule {
        @Override
        public String violation(Object value) {
            return value instanceof String s && values.contains(s) ? null : "недопустимое значение перечисления";
        }
    }

    record Text(Pattern pattern) implements FieldRule {
        @Override
        public String violation(Object value) {
            return value instanceof String s && pattern.matcher(s).matches() ? null : "недопустимый формат строки";
        }
    }
}
