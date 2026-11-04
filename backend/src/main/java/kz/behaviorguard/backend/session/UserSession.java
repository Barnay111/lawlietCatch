package kz.behaviorguard.backend.session;

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
import kz.behaviorguard.backend.user.AnalyzedUser;

import java.time.Instant;
import java.util.UUID;

/** Пользовательская сессия (раздел 4.3 ТЗ). */
@Entity
@Table(name = "sessions")
public class UserSession {

    public enum DeviceType { DESKTOP, MOBILE, TABLET }

    public enum Status { ACTIVE, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AnalyzedUser user;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 30, updatable = false)
    private DeviceType deviceType;

    @Column(name = "sdk_version", nullable = false, length = 20, updatable = false)
    private String sdkVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "event_count", nullable = false)
    private int eventCount;

    /** Последняя оценка риска по сессии. Обновляется при каждой новой оценке (см. recordAssessment). */
    @Enumerated(EnumType.STRING)
    @Column(name = "latest_risk_level", length = 20)
    private kz.behaviorguard.backend.risk.RiskPolicy.RiskLevel latestRiskLevel;

    @Column(name = "latest_risk_at")
    private Instant latestRiskAt;

    protected UserSession() {
    }

    public UserSession(Organization organization, AnalyzedUser user, Instant startedAt,
                       DeviceType deviceType, String sdkVersion) {
        this.organization = organization;
        this.user = user;
        this.startedAt = startedAt;
        this.deviceType = deviceType;
        this.sdkVersion = sdkVersion;
    }

    /** Завершает сессию. Повторный вызов ничего не меняет. */
    public void finish(Instant at) {
        if (status == Status.ACTIVE) {
            status = Status.COMPLETED;
            endedAt = at;
        }
    }

    /** Учитывает оценку, если она новее текущей последней. */
    public void recordAssessment(kz.behaviorguard.backend.risk.RiskPolicy.RiskLevel level, Instant at) {
        if (latestRiskAt == null || !at.isBefore(latestRiskAt)) {
            latestRiskLevel = level;
            latestRiskAt = at;
        }
    }

    public void addEvents(int count) {
        eventCount += count;
    }

    public UUID getId() { return id; }
    public Organization getOrganization() { return organization; }
    public AnalyzedUser getUser() { return user; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public DeviceType getDeviceType() { return deviceType; }
    public String getSdkVersion() { return sdkVersion; }
    public Status getStatus() { return status; }
    public int getEventCount() { return eventCount; }
    public kz.behaviorguard.backend.risk.RiskPolicy.RiskLevel getLatestRiskLevel() { return latestRiskLevel; }
    public Instant getLatestRiskAt() { return latestRiskAt; }
}
