package in.eko.eloka.onboarding.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** What a reviewer asked the applicant to fix (T5), and by when. */
@Entity
@Table(name = "correction_request")
class CorrectionRequest extends BaseEntity {

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "staff_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID staffId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "step_keys", nullable = false, columnDefinition = "JSON")
	private String stepKeys;

	@Column(name = "reason_code", nullable = false, length = 40)
	private String reasonCode;

	@Column(nullable = false, length = 1000)
	private String message;

	@Column(name = "due_at", nullable = false)
	private Instant dueAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	protected CorrectionRequest() {
	}

	CorrectionRequest(UUID applicationId, UUID staffId, String stepKeysJson, String reasonCode, String message,
			Instant dueAt) {
		this.applicationId = applicationId;
		this.staffId = staffId;
		this.stepKeys = stepKeysJson;
		this.reasonCode = reasonCode;
		this.message = message;
		this.dueAt = dueAt;
	}

	void resolve(Instant now) {
		if (resolvedAt == null) {
			resolvedAt = now;
		}
	}

	boolean isOpen() {
		return resolvedAt == null;
	}

	String getStepKeys() {
		return stepKeys;
	}

	String getReasonCode() {
		return reasonCode;
	}

	String getMessage() {
		return message;
	}

	Instant getDueAt() {
		return dueAt;
	}
}
