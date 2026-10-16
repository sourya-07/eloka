package in.eko.eloka.onboarding;

/** Progress of one step of an application (docs/onboarding/ONBOARDING_STATE_MACHINE.md §4.2). */
public enum StepStatus {
	NOT_STARTED,
	IN_PROGRESS,
	PENDING_EXTERNAL,
	COMPLETED,
	FAILED,
	BLOCKED,
	SKIPPED,
	LOCKED,
	REOPENED,
	NOT_APPLICABLE;

	/** Counts as done for prerequisites and for submitting the application. */
	public boolean isSatisfied() {
		return this == COMPLETED || this == SKIPPED || this == NOT_APPLICABLE || this == LOCKED;
	}
}
