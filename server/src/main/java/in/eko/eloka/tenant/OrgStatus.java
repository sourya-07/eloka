package in.eko.eloka.tenant;

/** Lifecycle of a tenant organisation. Only ACTIVE orgs serve logins. */
public enum OrgStatus {
	INACTIVE,
	ACTIVE,
	SUSPENDED
}
