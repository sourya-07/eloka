package in.eko.eloka.network;

import java.time.LocalDate;
import java.util.UUID;
import in.eko.eloka.shared.security.Role;

/**
 * A user as other modules see it. Contains decrypted contact data, so never log it.
 */
public record UserView(UUID id, long orgId, String userCode, int userType, boolean orgAdmin, UserStatus status,
		String name, String mobile, String email, LocalDate dateOfJoining, boolean demo, UUID parentUserId,
		PersonalInfo personal, String gstin) {

	public Role role() {
		return UserTypes.roleOf(userType, orgAdmin);
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}
}
