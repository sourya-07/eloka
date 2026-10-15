package in.eko.eloka.gateway.internal.handler.network;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import in.eko.eloka.gateway.internal.handler.EpsValues;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserView;

/** Maps users to the "agent" objects of the EPS network endpoints. */
final class AgentRows {

	private AgentRows() {
	}

	/** One row of {@code data.agent_details} in the network list. */
	static Map<String, Object> listRow(UserView user, ShopView shop, BigDecimal balance) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("agent_name", user.name());
		row.put("agent_mobile", user.mobile());
		row.put("user_id", user.userCode());
		row.put("eko_code", user.userCode());
		row.put("user_type_id", user.userType());
		row.put("agent_type", EpsValues.typeLabel(user.userType()));
		row.put("account_status_id", EpsValues.statusId(user));
		row.put("account_status", EpsValues.statusLabel(user));
		row.put("onboarded_on", user.dateOfJoining().toString());
		row.put("location", shop.location());
		row.put("active_location", shop.location());
		row.put("onboarding_location", shop.location());
		Map<String, Object> profile = new LinkedHashMap<>();
		profile.put("eko_code", user.userCode());
		profile.put("wallet_balance", balance);
		profile.put("shop_name", shop.shopName());
		profile.put("shop_type", shop.shopType());
		profile.put("plan_name", null);
		row.put("profile", profile);
		return row;
	}

	/** The agent profile ({@code search_value} lookup): the list row plus address and personal details. */
	static Map<String, Object> detail(UserView user, ShopView shop, BigDecimal balance) {
		Map<String, Object> row = listRow(user, shop, balance);
		row.put("status", EpsValues.statusLabel(user));
		row.put("demo_account_expiry_date", user.demoExpiresOn() == null ? null
				: user.demoExpiresOn().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)));
		row.put("line_1", shop.addressLine());
		row.put("line_2", shop.addressLine2());
		row.put("city", shop.city());
		row.put("state", shop.state());
		row.put("zip", shop.pincode());
		Map<String, Object> personal = new LinkedHashMap<>();
		personal.put("dob", user.personal().dob() == null ? null : user.personal().dob().toString());
		personal.put("gender", user.personal().gender());
		personal.put("marital_status", user.personal().maritalStatus());
		personal.put("shop_name", shop.shopName());
		personal.put("shop_type", shop.shopType());
		row.put("personal_information", personal);
		Map<String, Object> address = new LinkedHashMap<>();
		address.put("ownership_type", shop.ownershipType());
		address.put("lattitude", shop.latitude()); // sic: the client reads this spelling
		address.put("longitude", shop.longitude());
		row.put("address_details", address);
		row.put("contact_information", java.util.Collections.singletonMap("email", user.email()));
		return row;
	}
}
