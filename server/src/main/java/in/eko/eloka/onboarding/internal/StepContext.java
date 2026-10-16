package in.eko.eloka.onboarding.internal;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.onboarding.StepKey;
import in.eko.eloka.onboarding.StepStatus;
import in.eko.eloka.shared.error.ApiProblem;
import org.springframework.http.HttpStatus;

/**
 * One write to an application, under its row lock. Gives step code what it needs and keeps the
 * step rules (prerequisites, dependency invalidation, timeline) in one place.
 */
final class StepContext {

	private final OnboardingApplication application;
	private final Map<StepKey, OnboardingStep> steps = new EnumMap<>(StepKey.class);
	private final BusinessProfileRepository profiles;
	private final TimelineEntryRepository timeline;
	private final Instant now;
	private final UUID sessionId;
	private BusinessProfile profile;

	StepContext(OnboardingApplication application, List<OnboardingStep> stepRows, BusinessProfileRepository profiles,
			TimelineEntryRepository timeline, Instant now, UUID sessionId) {
		this.application = application;
		stepRows.forEach(step -> steps.put(step.getStepKey(), step));
		this.profiles = profiles;
		this.timeline = timeline;
		this.now = now;
		this.sessionId = sessionId;
	}

	OnboardingApplication application() {
		return application;
	}

	UUID applicationId() {
		return application.getId();
	}

	Instant now() {
		return now;
	}

	/** Session of the applicant making this call (for step-up checks). */
	UUID sessionId() {
		return sessionId;
	}

	OnboardingStep step(StepKey key) {
		return steps.get(key);
	}

	/** The business profile, created on first use. */
	BusinessProfile profile() {
		if (profile == null) {
			profile = profiles.findByApplicationId(application.getId())
				.orElseGet(() -> profiles.save(new BusinessProfile(application.getId())));
		}
		return profile;
	}

	/** Every prerequisite of {@code key} is satisfied, or {@code 409 PREREQUISITE_INCOMPLETE}. */
	void requirePrerequisites(StepKey key) {
		List<String> missing = key.prerequisites().stream()
			.filter(prerequisite -> !steps.get(prerequisite).getStatus().isSatisfied())
			.map(Enum::name)
			.toList();
		if (!missing.isEmpty()) {
			throw new ApiProblem(HttpStatus.CONFLICT, "PREREQUISITE_INCOMPLETE", "Finish the earlier steps first",
					Map.of("missing_steps", missing));
		}
	}

	/**
	 * Marks the step done and resets steps that were completed on the old data (§4.3). Completing
	 * again with an identical result changes nothing downstream.
	 */
	void complete(StepKey key, String summaryJson) {
		OnboardingStep step = steps.get(key);
		boolean sameAsBefore = step.getStatus() == StepStatus.COMPLETED && summaryJson.equals(step.getSummary());
		step.complete(summaryJson, now);
		if (sameAsBefore) {
			return;
		}
		invalidateDependents(key, java.util.EnumSet.noneOf(StepKey.class));
		record("STEP_COMPLETED", key);
	}

	private void invalidateDependents(StepKey changed, java.util.Set<StepKey> visited) {
		for (StepKey dependent : changed.invalidates()) {
			if (!visited.add(dependent)) {
				continue;
			}
			OnboardingStep downstream = steps.get(dependent);
			if (downstream.getStatus() == StepStatus.COMPLETED || downstream.getStatus() == StepStatus.PENDING_EXTERNAL) {
				downstream.invalidate();
				record("STEP_INVALIDATED", dependent);
				invalidateDependents(dependent, visited);
			}
			else if (downstream.getStatus() == StepStatus.LOCKED
					&& application.getStatus() == in.eko.eloka.onboarding.ApplicationStatus.CORRECTION_REQUIRED) {
				// T6: a corrected step changes what was declared and signed, so those are redone too
				downstream.reopen("DEPENDENCY_CHANGED", null);
				record("STEP_REOPENED", dependent);
				invalidateDependents(dependent, visited);
			}
		}
	}

	void fail(StepKey key, String errorCode) {
		steps.get(key).fail(errorCode);
		record("STEP_FAILED", key);
	}

	void awaitExternal(StepKey key) {
		steps.get(key).awaitExternal();
		record("STEP_PENDING_EXTERNAL", key);
	}

	void record(String eventType, StepKey key) {
		timeline.save(new TimelineEntry(application.getId(), eventType, key, null, null, "APPLICANT", now));
	}

	Map<StepKey, OnboardingStep> steps() {
		return steps;
	}
}
