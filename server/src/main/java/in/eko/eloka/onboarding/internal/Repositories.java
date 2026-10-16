package in.eko.eloka.onboarding.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.onboarding.ApplicationStatus;
import in.eko.eloka.onboarding.StepKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ApplicantRepository extends JpaRepository<Applicant, UUID> {

	Optional<Applicant> findByMobileHash(byte[] mobileHash);
}

interface OnboardingApplicationRepository extends JpaRepository<OnboardingApplication, UUID> {

	Optional<OnboardingApplication> findByPublicIdAndApplicantId(String publicId, UUID applicantId);

	List<OnboardingApplication> findByApplicantIdAndStatusNotIn(UUID applicantId, Collection<ApplicationStatus> excluded);

	boolean existsByApplicantIdAndStatus(UUID applicantId, ApplicationStatus status);

	/** Write path: one writer per application at a time (state machine doc §3.3). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from OnboardingApplication a where a.publicId = :publicId and a.applicantId = :applicantId")
	Optional<OnboardingApplication> lockByPublicIdAndApplicantId(@Param("publicId") String publicId,
			@Param("applicantId") UUID applicantId);

	@Query("select a.id from OnboardingApplication a where a.status in :statuses and a.lastApplicantActivityAt < :cutoff")
	List<UUID> findIdleIds(@Param("statuses") Collection<ApplicationStatus> statuses, @Param("cutoff") Instant cutoff);

	/** Another live application already holds this PAN (E5). The DB unique key is the backstop. */
	@Query("select count(a) > 0 from OnboardingApplication a where a.panHash = :hash and a.id <> :id and a.status not in :closed")
	boolean existsLivePan(@Param("hash") byte[] hash, @Param("id") UUID excludeId,
			@Param("closed") Collection<ApplicationStatus> closed);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from OnboardingApplication a where a.id = :id")
	Optional<OnboardingApplication> lockById(@Param("id") UUID id);
}

interface OnboardingStepRepository extends JpaRepository<OnboardingStep, UUID> {

	List<OnboardingStep> findByApplicationId(UUID applicationId);

	Optional<OnboardingStep> findByApplicationIdAndStepKey(UUID applicationId, StepKey stepKey);
}

interface BusinessProfileRepository extends JpaRepository<BusinessProfile, UUID> {

	Optional<BusinessProfile> findByApplicationId(UUID applicationId);
}

interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {
}

interface TimelineEntryRepository extends JpaRepository<TimelineEntry, UUID> {

	List<TimelineEntry> findByApplicationIdOrderByOccurredAtAsc(UUID applicationId);
}

interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, UUID> {

	List<ApplicationDocument> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

	Optional<ApplicationDocument> findByPublicIdAndApplicationId(String publicId, UUID applicationId);
}
