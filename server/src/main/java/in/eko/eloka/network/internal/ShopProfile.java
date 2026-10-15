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

	protected ShopProfile() {
	}

	ShopProfile(UUID userId, ShopView shop) {
		this.userId = userId;
		update(shop);
	}

	void update(ShopView shop) {
		this.shopName = shop.shopName();
		this.shopType = shop.shopType();
		this.addressLine = shop.addressLine();
		this.city = shop.city();
		this.state = shop.state();
		this.pincode = shop.pincode();
	}

	ShopView toView() {
		return new ShopView(shopName, shopType, addressLine, city, state, pincode);
	}
}
