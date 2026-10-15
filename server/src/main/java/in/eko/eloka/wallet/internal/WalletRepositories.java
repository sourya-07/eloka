package in.eko.eloka.wallet.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WalletAccountRepository extends JpaRepository<WalletAccount, Long> {

	List<WalletAccount> findByOrgIdAndUserIdOrderById(long orgId, UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from WalletAccount a where a.orgId = :orgId and a.userId = :userId and a.typeId = :typeId")
	Optional<WalletAccount> lockByOwner(@Param("orgId") long orgId, @Param("userId") UUID userId, @Param("typeId") int typeId);

	Optional<WalletAccount> findByOrgIdAndUserIdAndTypeId(long orgId, UUID userId, int typeId);
}

interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

	boolean existsByAccountIdAndReference(long accountId, String reference);

	org.springframework.data.domain.Page<LedgerEntry> findByAccountIdAndPostedAtGreaterThanEqualAndPostedAtLessThan(long accountId,
			java.time.Instant from, java.time.Instant to, org.springframework.data.domain.Pageable page);
}
