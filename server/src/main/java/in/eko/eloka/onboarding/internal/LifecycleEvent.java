package in.eko.eloka.onboarding.internal;

/** Events that move an application between statuses (state machine doc §3.3, P7 subset). */
enum LifecycleEvent {
	/** T3 / T6: the applicant finished (or resubmitted after corrections). */
	SUBMIT,
	/** T16: the applicant withdraws. */
	WITHDRAW,
	/** T15: no applicant activity within the idle window. */
	EXPIRE,
	/** T4: a reviewer takes the case. */
	CLAIM,
	/** The reviewer gives the case back to the queue. */
	RELEASE,
	/** T5: the reviewer reopens steps for the applicant. */
	REQUEST_CORRECTION,
	/** T7: the checker approves. */
	APPROVE,
	/** T8 */
	REJECT,
	/** T9 */
	HOLD,
	/** Hold released: back to review. */
	RELEASE_HOLD
}
