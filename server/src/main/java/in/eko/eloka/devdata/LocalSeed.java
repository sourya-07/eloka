package in.eko.eloka.devdata;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Shape of {@code devdata/local-seed.json}. */
record LocalSeed(List<SeedOrg> orgs) {

	record SeedOrg(String key, String name, String appName, String logoUrl, List<String> subdomains,
			List<String> extraHostnames, Map<String, JsonNode> metadata, List<SeedUser> users,
			List<SeedNotification> notifications) {
	}

	record SeedUser(String mobile, String code, String parent, String name, String email, int userType, boolean orgAdmin,
			SeedShop shop, java.math.BigDecimal balance, java.time.LocalDate joined, boolean transactions,
			List<String> kycDone) {
	}

	record SeedNotification(String type, String title, String body, int priority, String link, String linkLabel) {
	}

	record SeedShop(String shopName, String shopType, String addressLine, String city, String state, String pincode) {
	}
}
