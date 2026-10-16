package in.eko.eloka.verification;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the verification module. Each call is one attempt, recorded with masked
 * results; the caller (onboarding) decides what the outcome means for its steps.
 */
public interface VerificationApi {

	PanResult verifyPan(UUID applicationId, String pan, String expectedName);

	/** Starts DigiLocker consent; the applicant is sent to {@code redirectUrl}. */
	KycSession startKyc(UUID applicationId, String publicBaseUrl);

	/** Current state of a KYC session; finalises it once the provider has the result. */
	KycResult kycStatus(UUID applicationId, String sessionId, String expectedName);

	BankResult verifyBank(UUID applicationId, BankAccountInput account, String expectedName);

	Optional<BankResult> bankAccount(UUID applicationId, String bankAccountId);

	enum Status {
		SUCCEEDED,
		FAILED,
		/** Valid, but the name does not match well enough: a reviewer decides (OQ-C3). */
		MANUAL_REVIEW,
		/** The provider has not answered yet. */
		PENDING
	}

	/** @param holderType 4th character of the PAN (C company, F firm/LLP, P person, …) */
	record PanResult(Status status, String verificationId, String panMasked, String nameAsPerPan, String holderType,
			int nameMatchScore, String failureCode) {
	}

	record KycSession(String sessionId, String redirectUrl) {
	}

	record KycResult(Status status, String name, String aadhaarMasked, int nameMatchScore, String failureCode) {
	}

	record BankAccountInput(String bankCode, String bankId, String ifsc, String accountNumber, UUID proofDocumentId) {
	}

	record BankResult(Status status, String bankAccountId, String accountMasked, String ifsc, String beneficiaryName,
			int nameMatchScore, String failureCode) {
	}
}
