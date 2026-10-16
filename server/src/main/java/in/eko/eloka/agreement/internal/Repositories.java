package in.eko.eloka.agreement.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AgreementRepository extends JpaRepository<Agreement, UUID> {

	List<Agreement> findByApplicationIdOrderByVersionNoDesc(UUID applicationId);

	Optional<Agreement> findByPublicIdAndApplicationId(String publicId, UUID applicationId);
}

interface EsignRequestRepository extends JpaRepository<EsignRequest, UUID> {

	List<EsignRequest> findByAgreementIdOrderByCreatedAtDesc(UUID agreementId);
}
