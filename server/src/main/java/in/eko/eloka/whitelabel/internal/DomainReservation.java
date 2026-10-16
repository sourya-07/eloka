package in.eko.eloka.whitelabel.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A sub-domain held for an application: RESERVED during onboarding, BOUND to a tenant in P9. */
@Entity
@Table(name = "domain_reservation")
class DomainReservation extends BaseEntity {

	static final String RESERVED = "RESERVED";
	static final String RELEASED = "RELEASED";

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(nullable = false, length = 63)
	private String label;

	@Column(nullable = false, length = 253)
	private String hostname;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "reserved_at", nullable = false)
	private Instant reservedAt;

	@Column(name = "released_at")
	private Instant releasedAt;

	protected DomainReservation() {
	}

	DomainReservation(UUID applicationId, String label, String hostname, Instant now) {
		this.applicationId = applicationId;
		this.label = label;
		this.hostname = hostname;
		this.status = RESERVED;
		this.reservedAt = now;
	}

	void release(Instant now) {
		if (RESERVED.equals(status)) {
			status = RELEASED;
			releasedAt = now;
		}
	}

	String getLabel() {
		return label;
	}

	String getHostname() {
		return hostname;
	}
}
