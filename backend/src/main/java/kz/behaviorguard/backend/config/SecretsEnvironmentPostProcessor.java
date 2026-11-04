package kz.behaviorguard.backend.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Проверка секретов до создания бинов: иначе Flyway успевает подключиться к БД с буквальной строкой
 * «${DB_PASSWORD}» и ошибка приходит позже и непонятно. Регистрируется в META-INF/spring.factories.
 */
public class SecretsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String message = "Не задан DB_PASSWORD. Создайте infra/.env из infra/.env.example и передайте DB_PASSWORD.";
        String pw;
        try {
            pw = environment.getProperty("spring.datasource.password");
        } catch (IllegalArgumentException unresolved) {
            // getProperty падает, если плейсхолдер ${DB_PASSWORD} не задан в окружении.
            throw new IllegalStateException(message, unresolved);
        }
        if (pw == null || pw.isBlank() || pw.contains("${")) {
            throw new IllegalStateException(message);
        }
    }
}
