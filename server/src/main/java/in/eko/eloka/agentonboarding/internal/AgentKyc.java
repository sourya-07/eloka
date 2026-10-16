package in.eko.eloka.agentonboarding.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * KYC data collected during onboarding (one row per user). PAN, phone numbers and bank account
 * are encrypted with a keyed hash and a masked copy; images are document ids.
 */
@Entity
@Table(name = "agent_kyc")
class AgentKyc extends BaseEntity {

	@Column(name = "org_id", nullable = false)
	private long orgId;

	@Column(name = "user_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
	private UUID userId;

	@Column(name = "pan_enc", columnDefinition = "VARBINARY(128)")
	private byte[] panEnc;

	@Column(name = "pan_hash", columnDefinition = "BINARY(32)")
	private byte[] panHash;

	@Column(name = "pan_masked", length = 10)
	private String panMasked;

	@Column(name = "pan_document_id", columnDefinition = "BINARY(16)")
	private UUID panDocumentId;

	@Column(name = "aadhaar_front_document_id", columnDefinition = "BINARY(16)")
	private UUID aadhaarFrontDocumentId;

	@Column(name = "aadhaar_back_document_id", columnDefinition = "BINARY(16)")
	private UUID aadhaarBackDocumentId;

	@Column(name = "selfie_document_id", columnDefinition = "BINARY(16)")
	private UUID selfieDocumentId;

	@Column(name = "latlong", length = 60)
	private String latlong;

	@Column(name = "company_type", length = 40)
	private String companyType;

	@Column(name = "authorized_signatory", length = 150)
	private String authorizedSignatory;

	@Column(name = "contact_mobile_enc", columnDefinition = "VARBINARY(128)")
	private byte[] contactMobileEnc;

	@Column(name = "alternate_mobile_enc", columnDefinition = "VARBINARY(128)")
	private byte[] alternateMobileEnc;

	@Column(name = "bank_code", length = 20)
	private String bankCode;

	@Column(name = "bank_id", length = 20)
	private String bankId;

	@Column(name = "bank_ifsc", length = 11)
	private String bankIfsc;

	@Column(name = "bank_account_enc", columnDefinition = "VARBINARY(128)")
	private byte[] bankAccountEnc;

	@Column(name = "bank_account_hash", columnDefinition = "BINARY(32)")
	private byte[] bankAccountHash;

	@Column(name = "bank_account_masked", length = 24)
	private String bankAccountMasked;

	@Column(name = "bank_proof_document_id", columnDefinition = "BINARY(16)")
	private UUID bankProofDocumentId;

	@Column(name = "digilocker_request_id", length = 80)
	private String digilockerRequestId;

	@Column(name = "agreement_document_id", length = 80)
	private String agreementDocumentId;

	@Column(name = "agreement_signed_at")
	private Instant agreementSignedAt;

	protected AgentKyc() {
	}

	AgentKyc(long orgId, UUID userId) {
		this.orgId = orgId;
		this.userId = userId;
	}

	void aadhaarImages(UUID front, UUID back) {
		this.aadhaarFrontDocumentId = front;
		this.aadhaarBackDocumentId = back;
	}

	void pan(byte[] enc, byte[] hash, String masked, UUID documentId) {
		this.panEnc = enc;
		this.panHash = hash;
		this.panMasked = masked;
		this.panDocumentId = documentId;
	}

	void location(String latlong) {
		this.latlong = latlong;
	}

	void selfie(UUID documentId) {
		this.selfieDocumentId = documentId;
	}

	void business(String companyType, String signatory, byte[] contactEnc, byte[] alternateEnc) {
		this.companyType = companyType;
		this.authorizedSignatory = signatory;
		this.contactMobileEnc = contactEnc;
		this.alternateMobileEnc = alternateEnc;
	}

	void bankAccount(String bankCode, String bankId, String ifsc, byte[] enc, byte[] hash, String masked) {
		this.bankCode = bankCode;
		this.bankId = bankId;
		this.bankIfsc = ifsc;
		this.bankAccountEnc = enc;
		this.bankAccountHash = hash;
		this.bankAccountMasked = masked;
	}

	boolean hasBankAccount() {
		return bankAccountEnc != null;
	}

	void bankProof(UUID documentId) {
		this.bankProofDocumentId = documentId;
	}

	void digiLocker(String requestId) {
		this.digilockerRequestId = requestId;
	}

	void agreement(String documentId, Instant signedAt) {
		this.agreementDocumentId = documentId;
		this.agreementSignedAt = signedAt;
	}

	UUID getUserId() {
		return userId;
	}

	String getPanMasked() {
		return panMasked;
	}

	String getBankAccountMasked() {
		return bankAccountMasked;
	}

	Instant getAgreementSignedAt() {
		return agreementSignedAt;
	}
}
