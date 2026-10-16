package in.eko.eloka.onboarding.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.onboarding.StepKey;
import in.eko.eloka.onboarding.StepStatus;
import in.eko.eloka.shared.error.ApiProblem;
import in.eko.eloka.shared.error.ConflictException;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;

/** One step of one application, with its own status machine (state machine doc §4.2). */
@Entity
@Table(name = "onboarding_step")
class OnboardingStep extends BaseEntity {

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "step_key", nullable = false, length = 30)
	private StepKey stepKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StepStatus status;

	@Column(nullable = false)
	private boolean required;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "max_attempts", nullable = false)
	private int maxAttempts;

	@Column(name = "last_error_code", length = 40)
	private String lastErrorCode;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "draft_data", columnDefinition = "JSON")
	private String draftData;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "JSON")
	private String summary;

	@Column(name = "reopen_reason_code", length = 40)
	private String reopenReasonCode;

	@Column(name = "reviewer_message", length = 1000)
	private String reviewerMessage;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected OnboardingStep() {
	}

	OnboardingStep(UUID applicationId, StepKey stepKey, boolean required, int maxAttempts) {
		this.applicationId = applicationId;
		this.stepKey = stepKey;
		this.required = required;
		this.maxAttempts = maxAttempts;
		this.status = StepStatus.NOT_STARTED;
	}

	/** Saves unverified data. Only for steps the applicant can still change. */
	void saveDraft(String json) {
		requireEditable();
		this.draftData = json;
		if (status == StepStatus.NOT_STARTED || status == StepStatus.SKIPPED || status == StepStatus.REOPENED) {
			status = StepStatus.IN_PROGRESS;
		}
	}

	/** Validated and verified. {@code summaryJson} is the applicant-safe view (masked values). */
	void complete(String summaryJson, Instant now) {
		requireAttemptAllowed();
		this.status = StepStatus.COMPLETED;
		this.summary = summaryJson;
		this.lastErrorCode = null;
		this.completedAt = now;
		this.draftData = null;
		this.reopenReasonCode = null;
		this.reviewerMessage = null;
	}

	/** Waiting for a provider (redirect, async check). */
	void awaitExternal() {
		requireAttemptAllowed();
		this.status = StepStatus.PENDING_EXTERNAL;
		this.lastErrorCode = null;
	}

	/** A failed verification attempt; the step blocks after {@code maxAttempts} failures. */
	void fail(String errorCode) {
		this.attemptCount++;
		this.lastErrorCode = errorCode;
		this.status = attemptCount >= maxAttempts ? StepStatus.BLOCKED : StepStatus.FAILED;
	}

	void skip() {
		if (required) {
			throw new ConflictException("STEP_REQUIRED", "This step cannot be skipped");
		}
		requireEditable();
		this.status = StepStatus.SKIPPED;
	}

	/** An upstream step changed, so this result is stale (§4.3). */
	void invalidate() {
		if (status == StepStatus.COMPLETED || status == StepStatus.PENDING_EXTERNAL) {
			status = StepStatus.IN_PROGRESS;
			completedAt = null;
		}
	}

	/** Application submitted: completed data is frozen. */
	void lock() {
		if (status == StepStatus.COMPLETED || status == StepStatus.SKIPPED) {
			status = StepStatus.LOCKED;
		}
	}

	/**
	 * A reviewer asks for this step again (T5); the applicant sees the reason and message.
	 *
	 * @return false when the step was never done (not started, not applicable), so there is nothing to reopen
	 */
	boolean reopen(String reasonCode, String message) {
		if (status == StepStatus.LOCKED || status == StepStatus.COMPLETED || status == StepStatus.SKIPPED) {
			this.status = StepStatus.REOPENED;
			this.reopenReasonCode = reasonCode;
			this.reviewerMessage = message;
			return true;
		}
		return false;
	}

	void markNotApplicable() {
		this.status = StepStatus.NOT_APPLICABLE;
		this.required = false;
	}

	/** Used when the program changes and a step applies again. */
	void markApplicable(boolean isRequired) {
		if (status == StepStatus.NOT_APPLICABLE) {
			status = StepStatus.NOT_STARTED;
		}
		this.required = isRequired;
	}

	private void requireEditable() {
		switch (status) {
			case LOCKED, BLOCKED, PENDING_EXTERNAL, NOT_APPLICABLE ->
				throw new ApiProblem(HttpStatus.FORBIDDEN, "STEP_NOT_EDITABLE", "This step cannot be changed now");
			default -> {
			}
		}
	}

	private void requireAttemptAllowed() {
		if (status == StepStatus.BLOCKED) {
			throw new ApiProblem(HttpStatus.LOCKED, "STEP_BLOCKED",
					"Too many failed attempts on this step. Please contact support.");
		}
		if (status == StepStatus.LOCKED || status == StepStatus.NOT_APPLICABLE) {
			throw new ApiProblem(HttpStatus.FORBIDDEN, "STEP_NOT_EDITABLE", "This step cannot be changed now");
		}
	}

	UUID getApplicationId() {
		return applicationId;
	}

	StepKey getStepKey() {
		return stepKey;
	}

	StepStatus getStatus() {
		return status;
	}

	boolean isRequired() {
		return required;
	}

	int getAttemptCount() {
		return attemptCount;
	}

	int getMaxAttempts() {
		return maxAttempts;
	}

	String getLastErrorCode() {
		return lastErrorCode;
	}

	String getDraftData() {
		return draftData;
	}

	String getSummary() {
		return summary;
	}

	String getReopenReasonCode() {
		return reopenReasonCode;
	}

	String getReviewerMessage() {
		return reviewerMessage;
	}
}
