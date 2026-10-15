package in.eko.eloka.network;

import in.eko.eloka.shared.security.Role;

/** EPS user type ids used by the client (constants/UserTypes.js). */
public final class UserTypes {

	public static final int DISTRIBUTOR = 1;
	public static final int MERCHANT = 2;
	public static final int I_MERCHANT = 3;
	public static final int FIELD_EXECUTIVE = 4;
	public static final int SUPER_DISTRIBUTOR = 7;
	public static final int ENTERPRISE_PARTNER = 23;
	public static final int ORG_ADMIN = 24;

	private UserTypes() {
	}

	public static Role roleOf(int userType, boolean orgAdmin) {
		if (orgAdmin || userType == ORG_ADMIN) {
			return Role.ORG_ADMIN;
		}
		if (userType == DISTRIBUTOR || userType == SUPER_DISTRIBUTOR) {
			return Role.DISTRIBUTOR;
		}
		return Role.RETAILER;
	}
}
