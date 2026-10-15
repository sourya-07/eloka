package in.eko.eloka.network.internal;

import java.util.UUID;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Shop / business location of a user (one per user). */
@Entity
@Table(name = "shop_profile")
class ShopProfile extends BaseEntity {

	@Column(name = "user_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
	private UUID userId;

	@Column(name = "shop_name", length = 150)
	private String shopName;

	@Column(name = "shop_type", length = 20)
	private String shopType;

	@Column(name = "address_line", length = 300)
	private String addressLine;

	@Column(length = 80)
	private String city;

	@Column(length = 80)
	private String state;

	@Column(length = 6)
	private String pincode;

	@Column(name = "address_line2", length = 300)
	private String addressLine2;

	@Column(length = 60)
	private String country;

	@Column(name = "ownership_type", length = 20)
	private String ownershipType;

	@Column(precision = 9, scale = 6)
	private java.math.BigDecimal latitude;

	@Column(precision = 9, scale = 6)
	private java.math.BigDecimal longitude;

	protected ShopProfile() {
	}

	ShopProfile(UUID userId, ShopView shop) {
		this.userId = userId;
		update(shop);
	}

	/** Copies every non-null field of {@code shop} (partial update). */
	void update(ShopView shop) {
		if (shop.shopName() != null) this.shopName = shop.shopName();
		if (shop.shopType() != null) this.shopType = shop.shopType();
		if (shop.addressLine() != null) this.addressLine = shop.addressLine();
		if (shop.addressLine2() != null) this.addressLine2 = shop.addressLine2();
		if (shop.city() != null) this.city = shop.city();
		if (shop.state() != null) this.state = shop.state();
		if (shop.pincode() != null) this.pincode = shop.pincode();
		if (shop.country() != null) this.country = shop.country();
		if (shop.ownershipType() != null) this.ownershipType = shop.ownershipType();
		if (shop.latitude() != null) this.latitude = shop.latitude();
		if (shop.longitude() != null) this.longitude = shop.longitude();
	}

	UUID getUserId() {
		return userId;
	}

	ShopView toView() {
		return new ShopView(shopName, shopType, addressLine, addressLine2, city, state, pincode, country, ownershipType,
				latitude, longitude);
	}
}
