package in.eko.eloka.onboarding.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.onboarding.ApplicationStatus;
import in.eko.eloka.onboarding.EntityType;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One Local Admin onboarding application. Status changes only through
 * {@link ApplicationStateMachine}; this class keeps the data consistent with the status.
 */
@Entity
@Table(name = "onboarding_application")
class OnboardingApplication extends BaseEntity {

	@Column(name = "public_id", nullable = false, unique = true, length = 32)
	private String publicId;

	@Column(name = "applicant_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicantId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ApplicationStatus status;

	@Column(name = "applicant_category", length = 20)
	private String applicantCategory;

	@Column(name = "product_type", length = 20)
	private String productType;

	@Enumerated(EnumType.STRING)
	@Column(name = "entity_type", length = 30)
	private EntityType entityType;

	@Column(name = "pan_hash", columnDefinition = "BINARY(32)")
	private byte[] panHash;

	@Column(name = "last_applicant_activity_at", nullable = false)
	private Instant lastApplicantActivityAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "status_changed_at", nullable = false)
	private Instant statusChangedAt;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	protected OnboardingApplication() {
	}

	OnboardingApplication(String publicId, UUID applicantId, Instant now, Duration idleExpiry) {
		this.publicId = publicId;
		this.applicantId = applicantId;
		this.status = ApplicationStatus.IN_PROGRESS;
		this.statusChangedAt = now;
		recordActivity(now, idleExpiry);
	}

	/** Every applicant write extends the idle window (and bumps the version used as ETag). */
	void recordActivity(Instant now, Duration idleExpiry) {
		this.lastApplicantActivityAt = now;
		this.expiresAt = now.plus(idleExpiry);
	}

	/** Called by {@link ApplicationStateMachine} only. */
	void moveTo(ApplicationStatus next, Instant now) {
		this.status = next;
		this.statusChangedAt = now;
		if (next == ApplicationStatus.SUBMITTED && submittedAt == null) {
			submittedAt = now;
		}
		if (next.isTerminal()) {
			closedAt = now;
		}
	}

	void selectProgram(String category, String product, EntityType type) {
		this.applicantCategory = category;
		this.productType = product;
		this.entityType = type;
	}

	/** The verified business PAN (blind index); unique among live applications. */
	void bindPan(byte[] hash) {
		this.panHash = hash;
	}

	boolean isIdleSince(Instant cutoff) {
		return lastApplicantActivityAt.isBefore(cutoff);
	}

	String getPublicId() {
		return publicId;
	}

	UUID getApplicantId() {
		return applicantId;
	}

	ApplicationStatus getStatus() {
		return status;
	}

	String getApplicantCategory() {
		return applicantCategory;
	}

	String getProductType() {
		return productType;
	}

	EntityType getEntityType() {
		return entityType;
	}

	Instant getExpiresAt() {
		return expiresAt;
	}

	Instant getSubmittedAt() {
		return submittedAt;
	}
}
