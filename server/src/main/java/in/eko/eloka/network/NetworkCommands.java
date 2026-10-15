package in.eko.eloka.network;

import java.util.List;
import in.eko.eloka.shared.security.Caller;

/**
 * Changes to users of the network. Every command checks that the target is in the caller's
 * scope; role changes and transfers are for org admins only. All changes are audited.
 */
public interface NetworkCommands {

	void changeStatus(Caller caller, String userCode, UserStatus status, String note);

	/** Retailer → independent retailer (type 3), reporting directly to the org. */
	void upgradeToIndependentRetailer(Caller caller, String mobile);

	/** Retailer → distributor (type 1), reporting directly to the org. */
	void promoteToDistributor(Caller caller, String mobile);

	/** Moves users (and everyone below them) under another distributor. */
	void transfer(Caller caller, String toDistributorCode, List<String> userCodes);

	/** @param userCode target, or {@code null} for the caller's own profile */
	void updatePersonal(Caller caller, String userCode, String name, PersonalInfo personal);

	/** Replaces the shop / address fields that are non-null in {@code shop}. */
	void updateShop(Caller caller, String userCode, ShopView shop);

	/** Stores the caller's business email after it was verified by OTP. */
	void setVerifiedBusinessEmail(Caller caller, String email);

	/** Removes a demo user (marks it DELETED). */
	void removeDemoUser(Caller caller, String userCode);
}
