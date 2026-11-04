package kz.behaviorguard.backend.admin;

/** Минимальные требования к паролю аналитика. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    private PasswordPolicy() {
    }

    public static void check(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("пароль: не короче " + MIN_LENGTH + " символов");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw new IllegalArgumentException("пароль: нужны буквы и цифры");
        }
    }
}
