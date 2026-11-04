package kz.behaviorguard.backend.risk;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Результат анализа (раздел 4.6 ТЗ). Неизменяемый: новая оценка — новая запись. */
@Entity
@Table(name = "risk_assessments")
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private UserSession session;

    /** Short, потому что колонка SMALLINT: Integer дал бы несовпадение при validate. */
    @Column(name = "risk_score", updatable = false)
    private Short riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20, updatable = false)
    private RiskPolicy.RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private RiskPolicy.Recommendation recommendation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private List<String> reasons;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> details;

    @Column(name = "model_version", nullable = false, length = 40, updatable = false)
    private String modelVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RiskAssessment() {
    }

    public RiskAssessment(Organization organization, UserSession session, Integer score,
                          RiskPolicy.RiskLevel level, RiskPolicy.Recommendation recommendation,
                          List<String> reasons, Map<String, Object> details, String modelVersion) {
        this.organization = organization;
        this.session = session;
        this.riskScore = score == null ? null : score.shortValue();
        this.riskLevel = level;
        this.recommendation = recommendation;
        this.reasons = reasons;
        this.details = details;
        this.modelVersion = modelVersion;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UserSession getSession() { return session; }
    public Short getRiskScore() { return riskScore; }
    public RiskPolicy.RiskLevel getRiskLevel() { return riskLevel; }
    public RiskPolicy.Recommendation getRecommendation() { return recommendation; }
    public List<String> getReasons() { return reasons; }
    public Map<String, Object> getDetails() { return details; }
    public String getModelVersion() { return modelVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
