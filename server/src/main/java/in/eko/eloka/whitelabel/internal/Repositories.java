package in.eko.eloka.whitelabel.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DomainReservationRepository extends JpaRepository<DomainReservation, UUID> {

	List<DomainReservation> findByApplicationIdAndStatus(UUID applicationId, String status);

	@Query("select count(r) > 0 from DomainReservation r where r.hostname = :hostname and r.status <> 'RELEASED'")
	boolean isHeld(@Param("hostname") String hostname);
}

interface BrandingDraftRepository extends JpaRepository<BrandingDraft, UUID> {

	Optional<BrandingDraft> findByApplicationId(UUID applicationId);
}
