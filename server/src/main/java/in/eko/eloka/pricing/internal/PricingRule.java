package in.eko.eloka.pricing.internal;

import java.math.BigDecimal;
import java.util.UUID;
import in.eko.eloka.pricing.PricingScope;
import in.eko.eloka.pricing.PricingValue;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One price/commission. Never edited: a new value supersedes the old rule (kept as history).
 * "No slab" is stored as 0–0.
 */
@Entity
@Table(name = "pricing_rule")
class PricingRule extends BaseEntity {

	@Column(name = "org_id", nullable = false)
	private long orgId;

	@Column(name = "product_id", nullable = false)
	private int productId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PricingScope scope;

	@Column(name = "target_user_id", columnDefinition = "BINARY(16)")
	private UUID targetUserId;

	@Column(name = "target_user_code", length = 20)
	private String targetUserCode;

	@Column(nullable = false)
	private boolean percent;

	@Column(nullable = false, precision = 12, scale = 4)
	private BigDecimal value;

	@Column(name = "slab_min", nullable = false, precision = 18, scale = 2)
	private BigDecimal slabMin;

	@Column(name = "slab_max", nullable = false, precision = 18, scale = 2)
	private BigDecimal slabMax;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "set_by", length = 20)
	private String setBy;

	protected PricingRule() {
	}

	PricingRule(long orgId, int productId, PricingScope scope, UUID targetUserId, String targetUserCode, boolean percent,
			BigDecimal value, BigDecimal slabMin, BigDecimal slabMax, String setBy) {
		this.orgId = orgId;
		this.productId = productId;
		this.scope = scope;
		this.targetUserId = targetUserId;
		this.targetUserCode = targetUserCode;
		this.percent = percent;
		this.value = value;
		this.slabMin = slabMin;
		this.slabMax = slabMax;
		this.setBy = setBy;
		this.active = true;
	}

	void supersede() {
		active = false;
	}

	int getProductId() { return productId; }
	PricingScope getScope() { return scope; }
	UUID getTargetUserId() { return targetUserId; }
	String getTargetUserCode() { return targetUserCode; }
	BigDecimal getSlabMin() { return slabMin; }
	BigDecimal getSlabMax() { return slabMax; }

	PricingValue toValue() {
		return new PricingValue(percent, value.stripTrailingZeros());
	}
}
