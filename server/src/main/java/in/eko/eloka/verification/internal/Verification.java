package in.eko.eloka.verification.internal;

import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One verification attempt (state machine doc §5.2). Results keep masked values only. */
@Entity
@Table(name = "verification")
class Verification extends BaseEntity {

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(nullable = false, length = 20)
	private String kind;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "subject_hash", columnDefinition = "BINARY(32)")
	private byte[] subjectHash;

	@Column(name = "provider_ref", length = 100)
	private String providerRef;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "JSON")
	private String result;

	@Column(name = "name_match_score")
	private Integer nameMatchScore;

	@Column(name = "failure_code", length = 40)
	private String failureCode;

	@Column(name = "decided_by", columnDefinition = "BINARY(16)")
	private UUID decidedBy;

	protected Verification() {
	}

	Verification(UUID applicationId, String kind, byte[] subjectHash, String providerRef) {
		this.applicationId = applicationId;
		this.kind = kind;
		this.subjectHash = subjectHash;
		this.providerRef = providerRef;
		this.status = "AWAITING_PROVIDER";
	}

	void succeed(String resultJson, Integer score, boolean needsReview) {
		this.status = needsReview ? "MANUAL_REVIEW" : "SUCCEEDED";
		this.result = resultJson;
		this.nameMatchScore = score;
		this.failureCode = null;
	}

	void fail(String code) {
		this.status = "FAILED";
		this.failureCode = code;
	}

	/** A reviewer's decision on a MANUAL_REVIEW result. */
	void decide(boolean accept, UUID staffId) {
		this.status = accept ? "SUCCEEDED" : "FAILED";
		// An accepted result is a success: no failure code may stay next to it
		this.failureCode = accept ? null : "REJECTED_BY_REVIEWER";
		this.decidedBy = staffId;
	}

	boolean isAwaitingReview() {
		return "MANUAL_REVIEW".equals(status);
	}

	boolean isAwaiting() {
		return "AWAITING_PROVIDER".equals(status);
	}

	UUID getApplicationId() {
		return applicationId;
	}

	String getKind() {
		return kind;
	}

	String getStatus() {
		return status;
	}

	String getProviderRef() {
		return providerRef;
	}

	String getResult() {
		return result;
	}

	Integer getNameMatchScore() {
		return nameMatchScore;
	}

	String getFailureCode() {
		return failureCode;
	}
}
