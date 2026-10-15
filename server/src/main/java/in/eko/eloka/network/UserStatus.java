package in.eko.eloka.network;

/** Account state. Only ACTIVE users can log in. DELETED users are hidden everywhere (removed demo users). */
public enum UserStatus {
	ACTIVE,
	INACTIVE,
	BLOCKED,
	DELETED
}
