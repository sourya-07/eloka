package in.eko.eloka.verification.internal;

import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A bank account an applicant submitted; the number is encrypted, with a blind index and a mask. */
@Entity
@Table(name = "bank_account")
class BankAccount extends BaseEntity {

	@Column(name = "application_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "public_id", nullable = false, unique = true, length = 32)
	private String publicId;

	@Column(name = "bank_code", nullable = false, length = 20)
	private String bankCode;

	@Column(name = "bank_id", length = 20)
	private String bankId;

	@Column(nullable = false, length = 11, columnDefinition = "CHAR(11)")
	private String ifsc;

	@Column(name = "account_enc", nullable = false, columnDefinition = "VARBINARY(128)")
	private byte[] accountEnc;

	@Column(name = "account_hash", nullable = false, columnDefinition = "BINARY(32)")
	private byte[] accountHash;

	@Column(name = "account_masked", nullable = false, length = 20)
	private String accountMasked;

	@Column(name = "proof_document_id", columnDefinition = "BINARY(16)")
	private UUID proofDocumentId;

	@Column(name = "verification_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID verificationId;

	@Column(name = "beneficiary_name", length = 150)
	private String beneficiaryName;

	protected BankAccount() {
	}

	BankAccount(UUID applicationId, String publicId, String bankCode, String bankId, String ifsc, byte[] accountEnc,
			byte[] accountHash, String accountMasked, UUID proofDocumentId, UUID verificationId) {
		this.applicationId = applicationId;
		this.publicId = publicId;
		this.bankCode = bankCode;
		this.bankId = bankId;
		this.ifsc = ifsc;
		this.accountEnc = accountEnc;
		this.accountHash = accountHash;
		this.accountMasked = accountMasked;
		this.proofDocumentId = proofDocumentId;
		this.verificationId = verificationId;
	}

	void beneficiary(String name) {
		this.beneficiaryName = name;
	}

	byte[] getAccountEnc() {
		return accountEnc;
	}

	String getPublicId() {
		return publicId;
	}

	String getIfsc() {
		return ifsc;
	}

	String getAccountMasked() {
		return accountMasked;
	}

	UUID getVerificationId() {
		return verificationId;
	}

	String getBeneficiaryName() {
		return beneficiaryName;
	}
}
