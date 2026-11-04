package kz.behaviorguard.backend.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kz.behaviorguard.backend.organization.Organization;
import kz.behaviorguard.backend.session.UserSession;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "behavioral_events")
public class BehavioralEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private UserSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40, updatable = false)
    private EventType eventType;

    @Column(name = "event_time", nullable = false, updatable = false)
    private Instant eventTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> payload;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected BehavioralEvent() {
    }

    public BehavioralEvent(Organization organization, UserSession session, EventType eventType,
                           Instant eventTime, Map<String, Object> payload) {
        this.organization = organization;
        this.session = session;
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.payload = payload;
        this.receivedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public EventType getEventType() { return eventType; }
    public Instant getEventTime() { return eventTime; }
    public Map<String, Object> getPayload() { return payload; }
}
