package kz.behaviorguard.backend.session;

import kz.behaviorguard.backend.risk.RiskPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    /** Поиск только внутри своей организации (multi-tenancy). */
    Optional<UserSession> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationIdAndStartedAtBetween(UUID organizationId, Instant from, Instant to);

    /** Сессии по последнему уровню риска, у которых последняя оценка попала в период. */
    @Query("""
            select s.latestRiskLevel as level, count(s) as cnt
            from UserSession s
            where s.organization.id = :orgId and s.latestRiskAt between :from and :to
            group by s.latestRiskLevel
            """)
    List<LevelCount> countByLatestRisk(@Param("orgId") UUID organizationId,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to);

    interface LevelCount {
        RiskPolicy.RiskLevel getLevel();
        long getCnt();
    }
}
