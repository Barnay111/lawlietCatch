package kz.behaviorguard.backend.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kz.behaviorguard.backend.organization.Organization;
import kz.behaviorguard.backend.user.AnalyzedUser;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Поведенческий профиль (раздел 4.5 ТЗ). Статистики обновляются по завершённым сессиям
 * алгоритмом Уэлфорда: хранятся mean, M2 и n, без списка сырых значений.
 */
@Entity
@Table(name = "behavioral_profiles")
public class BehavioralProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AnalyzedUser user;

    @Column(name = "profile_version", nullable = false)
    private int profileVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> features = new HashMap<>();

    @Column(name = "session_count", nullable = false)
    private int sessionCount;

    @Column(name = "model_version", length = 40)
    private String modelVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BehavioralProfile() {
    }

    public BehavioralProfile(Organization organization, AnalyzedUser user) {
        this.organization = organization;
        this.user = user;
        this.updatedAt = Instant.now();
    }

    /** Учитывает одну завершённую сессию. Признаки без значения пропускаются. */
    public void learn(Map<String, Double> sessionFeatures) {
        Map<String, Object> next = new HashMap<>(features);
        for (Map.Entry<String, Double> e : sessionFeatures.entrySet()) {
            if (e.getValue() == null) continue;
            next.put(e.getKey(), Welford.update(asStats(next.get(e.getKey())), e.getValue()));
        }
        this.features = next;
        this.sessionCount += 1;
        this.updatedAt = Instant.now();
    }

    /** Текущие статистики по признаку или null, если признака ещё не было. */
    public Map<String, Object> statsFor(String feature) {
        Object raw = features.get(feature);
        return raw instanceof Map<?, ?> m ? castMap(m) : null;
    }

    public Map<String, Object> getFeatures() { return features; }
    public int getSessionCount() { return sessionCount; }
    public int getProfileVersion() { return profileVersion; }
    public String getModelVersion() { return modelVersion; }
    public Instant getUpdatedAt() { return updatedAt; }
    public AnalyzedUser getUser() { return user; }

    private static Welford.State asStats(Object raw) {
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> map = castMap(m);
            return new Welford.State(
                    ((Number) map.get("mean")).doubleValue(),
                    ((Number) map.get("m2")).doubleValue(),
                    ((Number) map.get("n")).longValue());
        }
        return new Welford.State(0, 0, 0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }
}
