package in.eko.eloka.tenant.internal;

import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One public metadata entry of a tenant, e.g. {@code theme} → JSON. */
@Entity
@Table(name = "org_metadata", uniqueConstraints = @UniqueConstraint(columnNames = { "org_id", "meta_key" }))
class OrgMetadataEntry extends BaseEntity {

	@Column(name = "org_id", nullable = false)
	private long orgId;

	@Column(name = "meta_key", nullable = false, length = 60)
	private String key;

	@Column(name = "meta_value", nullable = false, length = 16000)
	private String value;

	protected OrgMetadataEntry() {
	}

	OrgMetadataEntry(long orgId, String key, String value) {
		this.orgId = orgId;
		this.key = key;
		this.value = value;
	}

	String getKey() {
		return key;
	}

	String getValue() {
		return value;
	}

	void replaceValue(String newValue) {
		this.value = newValue;
	}
}
