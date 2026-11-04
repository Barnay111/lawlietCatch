package kz.behaviorguard.backend.erasure;

import kz.behaviorguard.backend.audit.AuditService;
import kz.behaviorguard.backend.user.AnalyzedUserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Удаление данных клиента по запросу (раздел 8 ТЗ).
 * Удаляются: оценки риска, события, сессии, профиль, запись о пользователе.
 * В журнал аудита попадает только хеш псевдонима и счётчики: сам псевдоним и данные в журнал не пишем.
 */
@Service
public class ErasureService {

    private final AnalyzedUserRepository users;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public ErasureService(AnalyzedUserRepository users, JdbcTemplate jdbc, AuditService audit) {
        this.users = users;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public record Result(UUID requestId, Map<String, Integer> deleted) {
    }

    /** @return null, если пользователь не найден в этой организации */
    @Transactional
    public Result erase(UUID organizationId, UUID actorAdminId, String externalUserId, String reason) {
        var user = users.findByOrganizationIdAndExternalUserId(organizationId, externalUserId).orElse(null);
        if (user == null) {
            return null;
        }
        UUID uid = user.getId();
        Map<String, Integer> deleted = new LinkedHashMap<>();
        // Порядок важен из-за внешних ключей: сначала то, что ссылается на сессии.
        deleted.put("riskAssessments", jdbc.update(
                "delete from risk_assessments where session_id in (select id from sessions where user_id = ?)", uid));
        deleted.put("behavioralEvents", jdbc.update(
                "delete from behavioral_events where session_id in (select id from sessions where user_id = ?)", uid));
        deleted.put("sessions", jdbc.update("delete from sessions where user_id = ?", uid));
        deleted.put("behavioralProfiles", jdbc.update("delete from behavioral_profiles where user_id = ?", uid));
        deleted.put("users", jdbc.update("delete from users where id = ?", uid));

        UUID requestId = UUID.randomUUID();
        audit.record(organizationId, "ADMIN", actorAdminId.toString(), "USER_DATA_ERASED",
                requestId.toString(), Map.of(
                        "pseudonymHash", sha256(organizationId + ":" + externalUserId),
                        "reason", reason,
                        "deleted", deleted));
        return new Result(requestId, deleted);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
