package kz.behaviorguard.backend.admin;

import java.util.UUID;

/** Аутентифицированный администратор (логин в дашборд). */
public record AdminPrincipal(UUID adminId, UUID organizationId, AdminUser.Role role, String username) {
}
