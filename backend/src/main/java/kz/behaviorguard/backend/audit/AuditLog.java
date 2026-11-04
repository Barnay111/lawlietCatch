package kz.behaviorguard.backend.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kz.behaviorguard.backend.organization.Organization;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Журнал действий. Записи не меняются и не удаляются приложением. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Column(name = "actor_type", nullable = false, length = 20, updatable = false)
    private String actorType;

    @Column(name = "actor_id", nullable = false, length = 64, updatable = false)
    private String actorId;

    @Column(nullable = false, length = 60, updatable = false)
    private String action;

    @Column(length = 200, updatable = false)
    private String target;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(Organization organization, String actorType, String actorId,
                    String action, String target, Map<String, Object> details) {
        this.organization = organization;
        this.actorType = actorType;
        this.actorId = actorId;
        this.action = action;
        this.target = target;
        this.details = details;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public Map<String, Object> getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
