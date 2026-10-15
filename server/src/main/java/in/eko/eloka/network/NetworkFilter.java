package in.eko.eloka.network;

import java.time.LocalDate;

/**
 * Filters for the network list. Every field is optional.
 *
 * @param statusGroup {@code Active}, {@code InActive} or {@code InProgress} (as the client sends them)
 * @param parentUserCode only users below this user (who must be in the caller's scope)
 * @param search exact mobile number or user code
 */
public record NetworkFilter(Integer userType, String statusGroup, String parentUserCode, LocalDate joinedFrom,
		LocalDate joinedTo, String search) {

	public static NetworkFilter none() {
		return new NetworkFilter(null, null, null, null, null, null);
	}
}
