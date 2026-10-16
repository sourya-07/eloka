package in.eko.eloka.onboarding.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Proof of what the applicant agreed to and when (append-only, one row per acceptance). */
@Entity
@Table(name = "consent_record")
class ConsentRecord extends BaseEntity {

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "consent_version", nullable = false, length = 20)
	private String consentVersion;

	@Column(name = "terms_version", nullable = false, length = 20)
	private String termsVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "JSON")
	private String purposes;

	@Column(name = "accepted_at", nullable = false)
	private Instant acceptedAt;

	protected ConsentRecord() {
	}

	ConsentRecord(UUID applicationId, String consentVersion, String termsVersion, String purposesJson, Instant acceptedAt) {
		this.applicationId = applicationId;
		this.consentVersion = consentVersion;
		this.termsVersion = termsVersion;
		this.purposes = purposesJson;
		this.acceptedAt = acceptedAt;
	}
}
