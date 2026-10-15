package in.eko.eloka.network;

import java.math.BigDecimal;

/** A user's shop / business location. All fields may be null. */
public record ShopView(String shopName, String shopType, String addressLine, String addressLine2, String city,
		String state, String pincode, String country, String ownershipType, BigDecimal latitude, BigDecimal longitude) {

	public static ShopView empty() {
		return new ShopView(null, null, null, null, null, null, null, null, null, null, null);
	}

	/** Short "City, State" text for tables. */
	public String location() {
		if (city == null && state == null) {
			return "";
		}
		return city == null ? state : state == null ? city : city + ", " + state;
	}
}
