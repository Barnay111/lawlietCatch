package kz.behaviorguard.backend.security;

import java.util.UUID;

/** Аутентифицированный вызывающий: интеграция конкретной организации. */
public record ApiKeyPrincipal(UUID organizationId, ApiKey.Type keyType) {
}
