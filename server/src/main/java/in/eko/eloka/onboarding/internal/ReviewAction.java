package in.eko.eloka.onboarding.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One staff action on a case (review history and the maker-checker record). Append-only. */
@Entity
@Table(name = "review_action")
class ReviewAction extends BaseEntity {

	static final String CLAIM = "CLAIM";
	static final String RELEASE = "RELEASE";
	static final String DOCUMENT = "DOCUMENT_DECISION";
	static final String VERIFICATION = "VERIFICATION_DECISION";
	static final String CORRECTION = "CORRECTION_REQUESTED";
	static final String RECOMMENDATION = "RECOMMENDATION";
	static final String APPROVE = "APPROVE";
	static final String REJECT = "REJECT";
	static final String HOLD = "HOLD";
	static final String RELEASE_HOLD = "RELEASE_HOLD";
	static final String PII_REVEAL = "PII_REVEAL";

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "staff_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID staffId;

	@Column(nullable = false, length = 30)
	private String action;

	@Column(length = 20)
	private String decision;

	@Column(name = "reason_code", length = 40)
	private String reasonCode;

	@Column(length = 1000)
	private String note;

	@Column(name = "target_ref", length = 255)
	private String targetRef;

	/** What a rejected applicant is told (V28); {@link #note} stays internal. */
	@Column(name = "applicant_message", length = 1000)
	private String applicantMessage;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected ReviewAction() {
	}

	ReviewAction(UUID applicationId, UUID staffId, String action, String decision, String reasonCode, String note,
			String targetRef, Instant occurredAt) {
		this.applicationId = applicationId;
		this.staffId = staffId;
		this.action = action;
		this.decision = decision;
		this.reasonCode = reasonCode;
		this.note = note;
		this.targetRef = targetRef;
		this.occurredAt = occurredAt;
	}

	ReviewAction withApplicantMessage(String message) {
		this.applicantMessage = message;
		return this;
	}

	String getApplicantMessage() {
		return applicantMessage;
	}

	UUID getStaffId() {
		return staffId;
	}

	String getAction() {
		return action;
	}

	String getDecision() {
		return decision;
	}

	String getReasonCode() {
		return reasonCode;
	}

	String getNote() {
		return note;
	}

	String getTargetRef() {
		return targetRef;
	}

	Instant getOccurredAt() {
		return occurredAt;
	}
}
