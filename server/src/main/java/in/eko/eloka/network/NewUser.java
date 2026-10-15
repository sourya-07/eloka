package in.eko.eloka.network;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Data to create a user.
 *
 * @param userCode explicit code (seed data only); {@code null} allocates the next one
 * @param parentUserId the distributor this user works under, or {@code null} (reports to the org)
 */
public record NewUser(long orgId, String mobile, String name, String email, int userType, boolean orgAdmin,
		UserStatus status, String userCode, LocalDate dateOfJoining, boolean demo, ShopView shop, UUID parentUserId) {
}
