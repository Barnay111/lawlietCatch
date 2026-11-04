package kz.behaviorguard.backend.security;

import kz.behaviorguard.backend.organization.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    @Query("""
           select k from ApiKey k join fetch k.organization o
           where k.keyHash = :keyHash and k.revokedAt is null and o.status = :status
           """)
    Optional<ApiKey> findUsableByKeyHash(@Param("keyHash") String keyHash,
                                         @Param("status") Organization.Status status);
}
