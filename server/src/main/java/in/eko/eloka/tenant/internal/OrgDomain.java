package in.eko.eloka.tenant.internal;

import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A host name that serves a tenant (platform sub-domain or custom domain). */
@Entity
@Table(name = "org_domain")
class OrgDomain extends BaseEntity {

	@Column(name = "org_id", nullable = false)
	private long orgId;

	@Column(nullable = false, unique = true, length = 253)
	private String hostname;

	@Column(nullable = false)
	private boolean active;

	protected OrgDomain() {
	}

	OrgDomain(long orgId, String hostname) {
		this.orgId = orgId;
		this.hostname = hostname.toLowerCase();
		this.active = true;
	}

	long getOrgId() {
		return orgId;
	}

	boolean isActive() {
		return active;
	}
}
