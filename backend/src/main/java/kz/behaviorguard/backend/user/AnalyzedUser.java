package kz.behaviorguard.backend.user;

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

import java.time.Instant;
import java.util.UUID;

/**
 * Анализируемый пользователь (раздел 4.2 ТЗ).
 * Хранится только псевдоним из системы клиента: ФИО, телефон, email, ИИН не сохраняем.
 */
@Entity
@Table(name = "users")
public class AnalyzedUser {

    public enum ProfileStatus { NEW, LEARNING, READY }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Column(name = "external_user_id", nullable = false, length = 128, updatable = false)
    private String externalUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_status", nullable = false, length = 20)
    private ProfileStatus profileStatus = ProfileStatus.NEW;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AnalyzedUser() {
    }

    public AnalyzedUser(Organization organization, String externalUserId) {
        this.organization = organization;
        this.externalUserId = externalUserId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void updateProfileStatus(ProfileStatus status) {
        this.profileStatus = status;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Organization getOrganization() { return organization; }
    public String getExternalUserId() { return externalUserId; }
    public ProfileStatus getProfileStatus() { return profileStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
