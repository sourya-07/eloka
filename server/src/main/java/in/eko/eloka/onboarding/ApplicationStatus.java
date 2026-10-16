package in.eko.eloka.onboarding;

import java.util.Set;

/** Lifecycle of a Local Admin onboarding application (docs/onboarding/ONBOARDING_STATE_MACHINE.md §3). */
public enum ApplicationStatus {
	IN_PROGRESS,
	SUBMITTED,
	UNDER_REVIEW,
	CORRECTION_REQUIRED,
	ON_HOLD,
	APPROVED,
	REJECTED,
	PROVISIONING,
	PROVISIONING_FAILED,
	GO_LIVE_PENDING,
	ACTIVE,
	WITHDRAWN,
	EXPIRED;

	private static final Set<ApplicationStatus> TERMINAL = Set.of(REJECTED, WITHDRAWN, EXPIRED, ACTIVE);

	/** No further transitions; a new application may be started (except after ACTIVE). */
	public boolean isTerminal() {
		return TERMINAL.contains(this);
	}

	/** The applicant may change step data. */
	public boolean isEditableByApplicant() {
		return this == IN_PROGRESS || this == CORRECTION_REQUIRED;
	}
}
