package in.eko.eloka.network;

import java.util.Map;
import in.eko.eloka.shared.security.Caller;

/**
 * Network-wide questions, always answered within the caller's scope: an org admin sees the
 * whole org, a distributor sees the users below them, everyone else sees nothing.
 */
public interface NetworkQueries {

	/** @return user type → active/total counts in the caller's scope (the caller excluded) */
	Map<Integer, ActiveCount> activeCountsByType(Caller caller);

	/**
	 * @return ids of the users the caller may see business data for: {@code null} means the whole
	 *         org (org admin); a distributor gets everyone below them; others get an empty list
	 */
	java.util.List<java.util.UUID> userIdsInScope(Caller caller);

	/** @return user type → number of users who joined in the period, within the caller's scope */
	Map<Integer, Long> joinedByType(Caller caller, java.time.LocalDate from, java.time.LocalDate to);
}
