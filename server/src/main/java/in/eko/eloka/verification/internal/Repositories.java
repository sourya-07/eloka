package in.eko.eloka.verification.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface VerificationRepository extends JpaRepository<Verification, UUID> {

	Optional<Verification> findByApplicationIdAndKindAndProviderRef(UUID applicationId, String kind, String providerRef);

	List<Verification> findByApplicationIdAndKindAndSubjectHashOrderByCreatedAtDesc(UUID applicationId, String kind,
			byte[] subjectHash);

	List<Verification> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

	Optional<Verification> findByIdAndApplicationId(UUID id, UUID applicationId);
}

interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

	Optional<BankAccount> findByPublicIdAndApplicationId(String publicId, UUID applicationId);

	List<BankAccount> findByApplicationId(UUID applicationId);

	@org.springframework.data.jpa.repository.Query("""
			select count(distinct b.applicationId) from BankAccount b
			where b.accountHash in (select o.accountHash from BankAccount o where o.applicationId = :applicationId)
			  and b.applicationId <> :applicationId""")
	long countReuse(@org.springframework.data.repository.query.Param("applicationId") UUID applicationId);
}
