package kz.behaviorguard.backend.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AdminTokenRepository extends JpaRepository<AdminToken, UUID> {

    @Query("select t from AdminToken t join fetch t.admin a join fetch a.organization where t.tokenHash = :hash")
    Optional<AdminToken> findByTokenHash(@Param("hash") String hash);
}
