package in.eko.eloka.network;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.shared.security.Caller;

/**
 * Network-wide questions, always answered within the caller's scope: an org admin sees the
 * whole org, a distributor sees the users below them, everyone else sees nothing.
 * Anything outside the scope is reported as "not found".
 */
public interface NetworkQueries {

	/** @return user type → active/total counts in the caller's scope (the caller excluded) */
	Map<Integer, ActiveCount> activeCountsByType(Caller caller);

	/**
	 * @return ids of the users the caller may see business data for: {@code null} means the whole
	 *         org (org admin); a distributor gets everyone below them; others get an empty list
	 */
	List<UUID> userIdsInScope(Caller caller);

	/** @return user type → number of users who joined in the period, within the caller's scope */
	Map<Integer, Long> joinedByType(Caller caller, LocalDate from, LocalDate to);

	/** Paged, filtered list of users in scope, newest first. Page numbers start at 1. */
	NetworkPage search(Caller caller, NetworkFilter filter, int page, int size);

	/** Every user in scope (optionally of one type), for pickers and trees. */
	List<UserView> members(Caller caller, Integer userType);

	/** @throws in.eko.eloka.shared.error.NotFoundException when not in scope */
	UserView findInScope(Caller caller, String mobileOrCode);

	/** Users of one org by id (no scope check; for enriching rows already scoped). */
	Map<UUID, UserView> byIds(long orgId, Collection<UUID> ids);

	/** Shop/address of a user in scope. */
	ShopView shopOf(Caller caller, UUID userId);
}
