package in.eko.eloka.identity;

import java.util.Set;
import java.util.UUID;

/** An Eko staff account (no secrets). */
public record StaffView(UUID id, String email, String displayName, Set<StaffRole> roles, boolean active) {

	public StaffView {
		roles = Set.copyOf(roles);
	}
}
