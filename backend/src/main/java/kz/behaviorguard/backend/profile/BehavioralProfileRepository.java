package kz.behaviorguard.backend.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BehavioralProfileRepository extends JpaRepository<BehavioralProfile, UUID> {

    Optional<BehavioralProfile> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
}
