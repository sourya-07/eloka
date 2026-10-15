package in.eko.eloka.shared.security;

/** What a session was created for. Each kind may only use its own routes. */
public enum SessionKind {
	/** A registered org user (retailer, distributor, org admin) on the EPS-compatible API. */
	USER,
	/** Mobile verified, but no account yet: may only self-onboard. */
	PRE_REGISTRATION,
	/** Someone applying to become a Local Admin (`/v1/onboarding`). */
	LA_APPLICANT,
	/** Eko staff (`/v1/admin`). */
	STAFF
}
