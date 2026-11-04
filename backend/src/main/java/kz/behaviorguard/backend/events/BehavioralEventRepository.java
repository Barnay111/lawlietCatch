package kz.behaviorguard.backend.events;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BehavioralEventRepository extends JpaRepository<BehavioralEvent, UUID> {

    long countBySessionIdAndOrganizationId(UUID sessionId, UUID organizationId);

    List<BehavioralEvent> findBySessionIdAndOrganizationId(UUID sessionId, UUID organizationId);

    /** Удаляет до batch самых старых событий, принятых раньше cutoff. */
    @Modifying
    @Query(value = """
            DELETE FROM behavioral_events
            WHERE id IN (SELECT id FROM behavioral_events WHERE received_at < :cutoff LIMIT :batch)
            """, nativeQuery = true)
    int deleteReceivedBefore(@Param("cutoff") java.time.Instant cutoff, @Param("batch") int batch);
}
