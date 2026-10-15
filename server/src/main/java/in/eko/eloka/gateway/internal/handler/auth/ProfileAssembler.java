package in.eko.eloka.gateway.internal.handler.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import in.eko.eloka.identity.TokenPair;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserProfile;
import in.eko.eloka.network.UserView;
import in.eko.eloka.wallet.WalletApi;
import org.springframework.stereotype.Component;

/**
 * Builds the user object the client stores after login, in the two shapes EPS used:
 * <ul>
 * <li>interaction 151: {@code user_detail / personal_detail / shop_detail / account_detail};</li>
 * <li>token and refresh-profile: {@code details / personal_details / shop_details / account_details}
 * plus the tokens (the client rebuilds its whole user state from this).</li>
 * </ul>
 */
@Component
public class ProfileAssembler {

	private final WalletApi wallets;

	public ProfileAssembler(WalletApi wallets) {
		this.wallets = wallets;
	}

	public Map<String, Object> profileData(UserProfile profile) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("user_detail", userDetail(profile.user()));
		data.put("personal_detail", personal(profile.user()));
		data.put("shop_detail", shop(profile.shop()));
		data.put("account_detail", accounts(profile.user()));
		return data;
	}

	public Map<String, Object> sessionObject(UserProfile profile, TokenPair tokens) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("access_token", tokens.accessToken());
		body.put("refresh_token", tokens.refreshToken());
		body.put("access_token_lite", tokens.accessToken());
		body.put("token_expiration", tokens.expiresIn().toSeconds());
		body.put("details", userDetail(profile.user()));
		body.put("personal_details", personal(profile.user()));
		body.put("shop_details", shop(profile.shop()));
		body.put("account_details", accounts(profile.user()));
		return body;
	}

	/** Session object for a verified mobile with no account yet: the client starts self-onboarding. */
	public Map<String, Object> preRegistrationObject(String mobile, TokenPair tokens) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("access_token", tokens.accessToken());
		body.put("refresh_token", tokens.refreshToken());
		body.put("access_token_lite", tokens.accessToken());
		body.put("token_expiration", tokens.expiresIn().toSeconds());
		body.put("details", Map.of("mobile", mobile, "onboarding", 1, "signup_mobile", mobile));
		return body;
	}

	private static Map<String, Object> userDetail(UserView user) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("name", user.name());
		detail.put("mobile", user.mobile());
		detail.put("login_id", user.mobile());
		detail.put("code", user.userCode());
		detail.put("user_code", user.userCode());
		detail.put("org_id", Long.toString(user.orgId()));
		detail.put("bc", Long.toString(user.orgId()));
		detail.put("user_type", user.userType());
		detail.put("is_org_admin", user.orgAdmin() ? 1 : 0);
		detail.put("email", user.email() == null ? "" : user.email());
		detail.put("date_of_joining", user.dateOfJoining().toString());
		detail.put("onboarding", 0);
		detail.put("onboarding_steps", List.of());
		detail.put("role_list", "");
		detail.put("is_demo", user.demo() ? 1 : 0);
		return detail;
	}

	private static Map<String, Object> personal(UserView user) {
		Map<String, Object> personal = new LinkedHashMap<>();
		personal.put("name", user.name());
		personal.put("email", user.email() == null ? "" : user.email());
		personal.put("mobile", user.mobile());
		personal.put("dob", user.personal().dob() == null ? null : user.personal().dob().toString());
		personal.put("gender", user.personal().gender());
		personal.put("marital_status", user.personal().maritalStatus());
		personal.put("qualification", user.personal().qualification());
		return personal;
	}

	private static Map<String, Object> shop(ShopView shop) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("shop_name", shop.shopName());
		details.put("shop_type", shop.shopType());
		details.put("shop_address", shop.addressLine());
		details.put("shop_address_line2", shop.addressLine2());
		details.put("city", shop.city());
		details.put("state", shop.state());
		details.put("pincode", shop.pincode());
		return details;
	}

	private Map<String, Object> accounts(UserView user) {
		List<Map<String, Object>> list = wallets.accountsOf(user.orgId(), user.id())
			.stream()
			.map(account -> Map.<String, Object>of("id", account.id(), "type_id", account.typeId(), "account_number",
					account.accountNumber()))
			.toList();
		return Map.of("account_list", list);
	}
}
