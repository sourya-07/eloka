package in.eko.eloka.onboarding.internal;

/** Events that move an application between statuses (state machine doc §3.3, P7 subset). */
enum LifecycleEvent {
	/** T3 / T6: the applicant finished (or resubmitted after corrections). */
	SUBMIT,
	/** T16: the applicant withdraws. */
	WITHDRAW,
	/** T15: no applicant activity within the idle window. */
	EXPIRE
}
