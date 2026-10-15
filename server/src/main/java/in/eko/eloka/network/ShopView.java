package in.eko.eloka.network;

/** A user's shop / business location. All fields may be null. */
public record ShopView(String shopName, String shopType, String addressLine, String city, String state,
		String pincode) {

	public static ShopView empty() {
		return new ShopView(null, null, null, null, null, null);
	}
}
