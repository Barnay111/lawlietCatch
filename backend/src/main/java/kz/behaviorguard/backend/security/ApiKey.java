package kz.behaviorguard.backend.security;

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
 * Ключ интеграции. В БД хранится только SHA-256 от ключа (поле keyHash).
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    public enum Type { PUBLIC_CLIENT, SERVER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false, length = 20, updatable = false)
    private Type keyType;

    @Column(name = "key_hash", nullable = false, length = 64, updatable = false)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, length = 16, updatable = false)
    private String keyPrefix;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() {
    }

    public ApiKey(Organization organization, Type keyType, String keyHash, String keyPrefix) {
        this.organization = organization;
        this.keyType = keyType;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Organization getOrganization() { return organization; }
    public Type getKeyType() { return keyType; }
    public String getKeyPrefix() { return keyPrefix; }
    public Instant getRevokedAt() { return revokedAt; }
}
