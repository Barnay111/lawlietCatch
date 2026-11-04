package kz.behaviorguard.backend.risk;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {

    Optional<RiskAssessment> findFirstBySessionIdAndOrganizationIdOrderByCreatedAtDesc(UUID sessionId, UUID organizationId);

    List<RiskAssessment> findBySessionIdAndOrganizationIdOrderByCreatedAtDesc(UUID sessionId, UUID organizationId);

    /** Оценки организации за период, по возрастанию времени (для агрегатов дашборда). */
    /**
     * Сессия и пользователь подгружаются одним запросом: иначе на каждую оценку уходит
     * отдельный SELECT (N+1). На 1 249 оценках это давало около 0.3 с.
     */
    @Query("""
            select r from RiskAssessment r
            join fetch r.session s
            join fetch s.user
            where r.organization.id = :orgId and r.createdAt between :from and :to
            order by r.createdAt asc
            """)
    List<RiskAssessment> findForOverview(@Param("orgId") UUID organizationId,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);

    /** Лента оценок дашборда с необязательным фильтром по уровню. */
    @Query(value = """
            select r from RiskAssessment r join fetch r.session s join fetch s.user
            where r.organization.id = :orgId
              and (:level is null or r.riskLevel = :level)
            order by r.createdAt desc
            """,
           countQuery = """
            select count(r) from RiskAssessment r
            where r.organization.id = :orgId
              and (:level is null or r.riskLevel = :level)
            """)
    Page<RiskAssessment> feed(@Param("orgId") UUID organizationId,
                              @Param("level") RiskPolicy.RiskLevel level,
                              Pageable pageable);

    /** Почасовой ряд по часам UTC. Метка содержит дату: иначе часы разных дней склеиваются. */
    @Query(value = """
            SELECT to_char(date_trunc('hour', created_at AT TIME ZONE 'UTC'), 'DD.MM HH24:00') AS hour,
                   risk_level AS level,
                   count(*) AS cnt
            FROM risk_assessments
            WHERE organization_id = :orgId AND created_at BETWEEN :from AND :to
            GROUP BY date_trunc('hour', created_at AT TIME ZONE 'UTC'),
                     to_char(date_trunc('hour', created_at AT TIME ZONE 'UTC'), 'DD.MM HH24:00'),
                     risk_level
            ORDER BY date_trunc('hour', created_at AT TIME ZONE 'UTC')
            """, nativeQuery = true)
    List<HourCount> countByHour(@Param("orgId") UUID organizationId,
                                @Param("from") Instant from,
                                @Param("to") Instant to);

    /** Последние оценки периода для таблицы обзора. Лимит задаёт Pageable. */
    @Query("""
            select r from RiskAssessment r
            join fetch r.session s
            join fetch s.user
            where r.organization.id = :orgId and r.createdAt between :from and :to
            order by r.createdAt desc
            """)
    List<RiskAssessment> findRecentForOverview(@Param("orgId") UUID organizationId,
                                               @Param("from") Instant from,
                                               @Param("to") Instant to,
                                               Pageable pageable);

    interface HourCount {
        String getHour();
        String getLevel();
        long getCnt();
    }
}
