package in.eko.eloka.verification.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface VerificationRepository extends JpaRepository<Verification, UUID> {

	Optional<Verification> findByApplicationIdAndKindAndProviderRef(UUID applicationId, String kind, String providerRef);

	List<Verification> findByApplicationIdAndKindAndSubjectHashOrderByCreatedAtDesc(UUID applicationId, String kind,
			byte[] subjectHash);
}

interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

	Optional<BankAccount> findByPublicIdAndApplicationId(String publicId, UUID applicationId);
}
