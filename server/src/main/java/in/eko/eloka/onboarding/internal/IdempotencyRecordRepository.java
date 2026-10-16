package in.eko.eloka.onboarding.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

	Optional<IdempotencyRecord> findByScopeIdAndIdemKey(UUID scopeId, String idemKey);

	@Modifying
	@Query("delete from IdempotencyRecord r where r.expiresAt < :now")
	int deleteExpired(@Param("now") Instant now);
}
