package in.eko.eloka.shared.security;

/** Business role of a caller. Derived from the user's type, never from request data. */
public enum Role {
	RETAILER,
	DISTRIBUTOR,
	ORG_ADMIN,
	PRE_REGISTRATION,
	LA_APPLICANT,
	STAFF;

	public boolean isAgent() {
		return this == RETAILER || this == DISTRIBUTOR;
	}
}
