package in.eko.eloka.network;

import java.util.Optional;
import java.util.UUID;

/** Read side of the network module. Every lookup is scoped to one org. */
public interface UserDirectory {

	Optional<UserView> findByMobile(long orgId, String mobile);

	Optional<UserView> findByUserCode(long orgId, String userCode);

	/** @throws in.eko.eloka.shared.error.NotFoundException when missing or in another org */
	UserView getById(long orgId, UUID userId);

	UserProfile profile(long orgId, UUID userId);
}
