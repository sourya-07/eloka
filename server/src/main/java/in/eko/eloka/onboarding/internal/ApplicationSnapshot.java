package in.eko.eloka.onboarding.internal;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * What the applicant sees after every call (API_SPECIFICATION.md §2). The UI renders from this
 * and never infers progress from positions. Field names are snake_case like the rest of the API.
 */
record ApplicationSnapshot(@JsonProperty("onboarding_id") String onboardingId, String status, long version,
		Program program, @JsonProperty("next_recommended_step") String nextRecommendedStep,
		@JsonProperty("expires_at") Instant expiresAt, @JsonProperty("submitted_at") Instant submittedAt, List<Step> steps,
		@JsonProperty("available_actions") List<String> availableActions, Correction correction) {

	/** What the reviewer asked to fix (only while {@code CORRECTION_REQUIRED}). */
	record Correction(@JsonProperty("reason_code") String reasonCode, String message, @JsonProperty("due_at") Instant dueAt,
			@JsonProperty("step_keys") JsonNode stepKeys) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record Program(@JsonProperty("applicant_category") String applicantCategory,
			@JsonProperty("product_type") String productType, @JsonProperty("entity_type") Integer entityType) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record Step(@JsonProperty("step_key") String stepKey, String status, boolean required,
			@JsonProperty("attempt_count") int attemptCount, @JsonProperty("max_attempts") int maxAttempts,
			@JsonProperty("last_error_code") String lastErrorCode, JsonNode summary, JsonNode draft,
			@JsonProperty("reopen_reason_code") String reopenReasonCode,
			@JsonProperty("reviewer_message") String reviewerMessage) {
	}
}
