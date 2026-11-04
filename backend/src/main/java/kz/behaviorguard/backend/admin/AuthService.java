package kz.behaviorguard.backend.admin;

import kz.behaviorguard.backend.audit.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    public static final Duration TOKEN_TTL = Duration.ofHours(8);

    private final String dummyHash;

    private static final SecureRandom RANDOM = new SecureRandom();

    public sealed interface Outcome permits Success, Failed, Locked {
    }

    public record Success(String token, Instant expiresAt, AdminUser.Role role, String username) implements Outcome {
    }

    public record Failed() implements Outcome {
    }

    public record Locked() implements Outcome {
    }

    private final AdminUserRepository admins;
    private final AdminTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AuthService(AdminUserRepository admins, AdminTokenRepository tokens,
                       PasswordEncoder encoder, AuditService audit) {
        this.admins = admins;
        this.tokens = tokens;
        this.encoder = encoder;
        this.audit = audit;
        // Хеш-заглушка для входа с несуществующим логином: время ответа не выдаёт наличие пользователя.
        this.dummyHash = encoder.encode(newRawToken());
    }

    /**
     * Не бросает исключений внутри транзакции: иначе откатится счётчик неудачных попыток.
     */
    @Transactional
    public Outcome login(String username, String password) {
        Instant now = Instant.now();
        Optional<AdminUser> found = admins.findByUsername(username);
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash);
            return new Failed();
        }
        AdminUser admin = found.get();
        if (admin.isLocked(now)) {
            audit.record(admin.getOrganization().getId(), "ADMIN", admin.getId().toString(),
                    "LOGIN_LOCKED", null, Map.of());
            return new Locked();
        }
        if (!encoder.matches(password, admin.getPasswordHash()) || admin.getStatus() != AdminUser.Status.ACTIVE) {
            admin.registerFailure(now);
            audit.record(admin.getOrganization().getId(), "ADMIN", admin.getId().toString(),
                    "LOGIN_FAILED", null, Map.of());
            return new Failed();
        }
        admin.registerSuccess(now);
        String raw = newRawToken();
        Instant expires = now.plus(TOKEN_TTL);
        tokens.save(new AdminToken(admin, sha256(raw), expires));
        audit.record(admin.getOrganization().getId(), "ADMIN", admin.getId().toString(),
                "LOGIN_SUCCESS", null, Map.of());
        return new Success(raw, expires, admin.getRole(), admin.getUsername());
    }

    @Transactional
    public void logout(String rawToken) {
        tokens.findByTokenHash(sha256(rawToken)).ifPresent(t -> {
            t.revoke(Instant.now());
            audit.record(t.getAdmin().getOrganization().getId(), "ADMIN", t.getAdmin().getId().toString(),
                    "LOGOUT", null, Map.of());
        });
    }

    public static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
