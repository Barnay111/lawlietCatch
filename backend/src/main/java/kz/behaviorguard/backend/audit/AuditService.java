package kz.behaviorguard.backend.audit;

import kz.behaviorguard.backend.organization.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository log;
    private final OrganizationRepository organizations;

    public AuditService(AuditLogRepository log, OrganizationRepository organizations) {
        this.log = log;
        this.organizations = organizations;
    }

    /**
     * Записывает действие в журнал в той же транзакции, что и основное действие.
     * В details не кладём персональные данные клиентов: только идентификаторы и счётчики.
     */
    /**
     * REQUIRES_NEW: запись не должна теряться внутри read-only транзакции вызывающего кода.
     * Без этого Hibernate молча не сбрасывает INSERT, и журнал оказывается пустым.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID organizationId, String actorType, String actorId,
                       String action, String target, Map<String, Object> details) {
        log.save(new AuditLog(organizations.getReferenceById(organizationId),
                actorType, actorId, action, target, details));
    }
}
