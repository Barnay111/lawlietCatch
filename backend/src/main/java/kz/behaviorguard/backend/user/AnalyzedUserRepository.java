package kz.behaviorguard.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AnalyzedUserRepository extends JpaRepository<AnalyzedUser, UUID> {

    /** Все запросы привязаны к организации: данные других организаций не видны. */
    Optional<AnalyzedUser> findByOrganizationIdAndExternalUserId(UUID organizationId, String externalUserId);

    Optional<AnalyzedUser> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
