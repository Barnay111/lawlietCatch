package kz.behaviorguard.backend.bootstrap;

import kz.behaviorguard.backend.admin.AdminUser;
import kz.behaviorguard.backend.admin.AdminUserRepository;
import kz.behaviorguard.backend.admin.AuthService;
import kz.behaviorguard.backend.organization.Organization;
import kz.behaviorguard.backend.organization.OrganizationRepository;
import kz.behaviorguard.backend.security.ApiKey;
import kz.behaviorguard.backend.security.ApiKeyService;
import org.springframework.boot.ApplicationArguments;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Только для профиля dev: создаёт демо-организацию и выводит её ключи один раз,
 * если организаций ещё нет. В production этого компонента не будет.
 */
@Component
@Profile("dev")
public class DevDataInitializer implements ApplicationRunner {

    private final OrganizationRepository organizations;
    private final ApiKeyService apiKeys;

    private final AdminUserRepository admins;
    private final PasswordEncoder encoder;

    public DevDataInitializer(OrganizationRepository organizations, ApiKeyService apiKeys,
                              AdminUserRepository admins, PasswordEncoder encoder) {
        this.organizations = organizations;
        this.apiKeys = apiKeys;
        this.admins = admins;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (organizations.count() == 0) {
            createDemoIntegration();
        }
        createDemoAdmins();
    }

    /** Один раз создаёт администратора и аналитика демо-организации. Пароли показываются один раз. */
    private void createDemoAdmins() {
        if (admins.count() > 0) {
            return;
        }
        Organization org = organizations.findAll().get(0);
        String adminPassword = AuthService.newRawToken();
        String analystPassword = AuthService.newRawToken();
        admins.save(new AdminUser(org, "admin", encoder.encode(adminPassword), AdminUser.Role.ADMIN));
        admins.save(new AdminUser(org, "analyst", encoder.encode(analystPassword), AdminUser.Role.ANALYST));
        writeCredentials(List.of(
                "# DEV: учётные записи дашборда. Файл создан автоматически, не коммитить.",
                "admin   / " + adminPassword + "   (роль ADMIN)",
                "analyst / " + analystPassword + "   (роль ANALYST)"));
    }

    private void createDemoIntegration() {
        Organization demo = organizations.save(new Organization("Demo Bank"));
        ApiKeyService.IssuedKey sdk = apiKeys.issue(demo, ApiKey.Type.PUBLIC_CLIENT);
        ApiKeyService.IssuedKey server = apiKeys.issue(demo, ApiKey.Type.SERVER);
        writeCredentials(List.of(
                "# DEV: демо-организация 'Demo Bank'. Файл создан автоматически, не коммитить.",
                "PUBLIC_CLIENT (для SDK):    " + sdk.rawKey(),
                "SERVER (для бэкенда банка): " + server.rawKey()));
    }

    private static void writeCredentials(List<String> lines) {
        try {
            var file = DevCredentialsFile.write(lines);
            System.out.println("DEV: учётные данные записаны в " + file + " (права 600). В лог не выводятся.");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Не удалось записать dev-учётные данные", e);
        }
    }
}
