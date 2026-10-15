package in.eko.eloka.tenant.internal;

import java.time.Instant;
import in.eko.eloka.tenant.OrgStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A tenant. Numeric id because the client exposes it as "org_id". */
@Entity
@Table(name = "org")
class Org {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(name = "app_name", nullable = false, length = 80)
	private String appName;

	@Column(name = "logo_url", length = 500)
	private String logoUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrgStatus status;

	@Version
	private long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Org() {
	}

	Org(String name, String appName, String logoUrl, OrgStatus status) {
		this.name = name;
		this.appName = appName;
		this.logoUrl = logoUrl;
		this.status = status;
	}

	Long getId() {
		return id;
	}

	String getName() {
		return name;
	}

	String getAppName() {
		return appName;
	}

	String getLogoUrl() {
		return logoUrl;
	}

	OrgStatus getStatus() {
		return status;
	}
}
