package kz.behaviorguard.backend.security;

import kz.behaviorguard.backend.organization.Organization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int KEY_BYTES = 32;

    private final ApiKeyRepository apiKeys;

    public ApiKeyService(ApiKeyRepository apiKeys) {
        this.apiKeys = apiKeys;
    }

    /** Выпускает новый ключ. Сырое значение возвращается один раз и больше нигде не хранится. */
    @Transactional
    public IssuedKey issue(Organization organization, ApiKey.Type type) {
        String prefix = type == ApiKey.Type.PUBLIC_CLIENT ? "bg_pk_" : "bg_sk_";
        byte[] random = new byte[KEY_BYTES];
        RANDOM.nextBytes(random);
        String raw = prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        ApiKey saved = apiKeys.save(new ApiKey(organization, type, sha256(raw), raw.substring(0, 10)));
        return new IssuedKey(raw, saved);
    }

    /** Возвращает принципала, если ключ существует, не отозван и организация активна. */
    @Transactional(readOnly = true)
    public Optional<ApiKeyPrincipal> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        return apiKeys.findUsableByKeyHash(sha256(rawKey), Organization.Status.ACTIVE)
                .map(k -> new ApiKeyPrincipal(k.getOrganization().getId(), k.getKeyType()));
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

    public record IssuedKey(String rawKey, ApiKey entity) {
    }
}
