package in.eko.eloka.tenant.internal;

import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One private business setting of an org. */
@Entity
@Table(name = "org_setting", uniqueConstraints = @UniqueConstraint(columnNames = { "org_id", "setting_key" }))
class OrgSetting extends BaseEntity {

	@Column(name = "org_id", nullable = false)
	private long orgId;

	@Column(name = "setting_key", nullable = false, length = 60)
	private String key;

	@Column(name = "setting_value", nullable = false, length = 4000)
	private String value;

	protected OrgSetting() {
	}

	OrgSetting(long orgId, String key, String value) {
		this.orgId = orgId;
		this.key = key;
		this.value = value;
	}

	String getValue() {
		return value;
	}

	void replace(String newValue) {
		this.value = newValue;
	}
}
