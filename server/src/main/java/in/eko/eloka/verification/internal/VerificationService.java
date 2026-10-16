package in.eko.eloka.verification.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.integration.BankAccountVerifier;
import in.eko.eloka.integration.DigiLockerProvider;
import in.eko.eloka.integration.PanVerificationProvider;
import in.eko.eloka.shared.crypto.BlindIndex;
import in.eko.eloka.shared.crypto.FieldCipher;
import in.eko.eloka.shared.crypto.Masking;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.persistence.PublicIds;
import in.eko.eloka.verification.NameMatch;
import in.eko.eloka.verification.VerificationApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs verifications through the provider ports and records each attempt. Name checks use
 * {@link NameMatch}: below the threshold the result is {@code MANUAL_REVIEW} (a reviewer decides).
 */
@Service
class VerificationService implements VerificationApi {

	private final VerificationRepository verifications;
	private final BankAccountRepository bankAccounts;
	private final PanVerificationProvider pans;
	private final DigiLockerProvider digiLocker;
	private final BankAccountVerifier banks;
	private final FieldCipher cipher;
	private final BlindIndex index;
	private final AuditLog audit;
	private final JsonMapper json;
	private final Clock clock;

	VerificationService(VerificationRepository verifications, BankAccountRepository bankAccounts,
			PanVerificationProvider pans, DigiLockerProvider digiLocker, BankAccountVerifier banks, FieldCipher cipher,
			BlindIndex index, AuditLog audit, JsonMapper json, Clock clock) {
		this.verifications = verifications;
		this.bankAccounts = bankAccounts;
		this.pans = pans;
		this.digiLocker = digiLocker;
		this.banks = banks;
		this.cipher = cipher;
		this.index = index;
		this.audit = audit;
		this.json = json;
		this.clock = clock;
	}

	@Override
	@Transactional
	public PanResult verifyPan(UUID applicationId, String pan, String expectedName) {
		Verification attempt = verifications.save(new Verification(applicationId, "PAN", index.hash("pan", pan), null));
		PanVerificationProvider.PanCheck check = pans.verify(pan, expectedName);
		String masked = Masking.pan(pan);
		switch (check.outcome()) {
			case PENDING -> {
				record(attempt, "PENDING");
				return new PanResult(Status.PENDING, attempt.getId().toString(), masked, null, null, 0, null);
			}
			case INVALID -> {
				attempt.fail("PAN_INVALID");
				record(attempt, "FAILED");
				return new PanResult(Status.FAILED, attempt.getId().toString(), masked, null, null, 0, "PAN_INVALID");
			}
			default -> {
				int score = expectedName == null ? 100 : NameMatch.score(expectedName, check.nameAsPerPan());
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("pan_masked", masked);
				result.put("name_as_per_pan", check.nameAsPerPan());
				result.put("holder_type", check.holderType());
				attempt.succeed(json.writeValueAsString(result), score, false);
				record(attempt, "SUCCEEDED");
				return new PanResult(Status.SUCCEEDED, attempt.getId().toString(), masked, check.nameAsPerPan(),
						check.holderType(), score, null);
			}
		}
	}

	@Override
	@Transactional
	public KycSession startKyc(UUID applicationId, String publicBaseUrl) {
		DigiLockerProvider.DigiLockerSession session = digiLocker.start(applicationId, publicBaseUrl);
		Verification attempt = verifications.save(new Verification(applicationId, "KYC", null, session.requestId()));
		record(attempt, "STARTED");
		return new KycSession(session.requestId(), session.link());
	}

	@Override
	@Transactional
	public KycResult kycStatus(UUID applicationId, String sessionId, String expectedName) {
		Verification attempt = verifications.findByApplicationIdAndKindAndProviderRef(applicationId, "KYC", sessionId)
			.orElseThrow(() -> new NotFoundException("KYC_SESSION_NOT_FOUND", "KYC session not found"));
		if (!attempt.isAwaiting()) {
			return kycView(attempt);
		}
		Optional<DigiLockerProvider.KycIdentity> identity = digiLocker.isCompleted(sessionId)
				? digiLocker.identity(sessionId, expectedName)
				: Optional.empty();
		if (identity.isEmpty()) {
			return new KycResult(Status.PENDING, null, null, 0, null);
		}
		int score = NameMatch.score(expectedName, identity.get().name());
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("name", identity.get().name());
		result.put("aadhaar_masked", identity.get().aadhaarMasked());
		attempt.succeed(json.writeValueAsString(result), score, score < NameMatch.MATCH_THRESHOLD);
		record(attempt, attempt.getStatus());
		return kycView(attempt);
	}

	@Override
	@Transactional
	public BankResult verifyBank(UUID applicationId, BankAccountInput input, String expectedName) {
		String ifsc = input.ifsc().toUpperCase(java.util.Locale.ROOT);
		byte[] accountHash = index.hash("bank_account", ifsc + ":" + input.accountNumber());
		Verification attempt = verifications.save(new Verification(applicationId, "BANK", accountHash, null));
		Instant now = Instant.now(clock);
		BankAccount account = bankAccounts.save(new BankAccount(applicationId, PublicIds.next("bnk", now), input.bankCode(),
				input.bankId(), ifsc, cipher.encrypt(input.accountNumber()), accountHash, Masking.account(input.accountNumber()),
				input.proofDocumentId(), attempt.getId()));
		BankAccountVerifier.BankCheck check = banks.verify(ifsc, input.accountNumber(), expectedName);
		if (!check.valid()) {
			attempt.fail(check.failureCode() == null ? "BANK_ACCOUNT_INVALID" : check.failureCode());
			record(attempt, "FAILED");
			return bankView(account, attempt);
		}
		account.beneficiary(check.beneficiaryName());
		int score = NameMatch.score(expectedName, check.beneficiaryName());
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("account_masked", account.getAccountMasked());
		result.put("ifsc", ifsc);
		result.put("beneficiary_name", check.beneficiaryName());
		attempt.succeed(json.writeValueAsString(result), score, score < NameMatch.MATCH_THRESHOLD);
		record(attempt, attempt.getStatus());
		return bankView(account, attempt);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<BankResult> bankAccount(UUID applicationId, String bankAccountId) {
		return bankAccounts.findByPublicIdAndApplicationId(bankAccountId, applicationId)
			.map(account -> bankView(account, verifications.findById(account.getVerificationId()).orElseThrow()));
	}

	@Override
	@Transactional(readOnly = true)
	public java.util.List<AttemptView> attempts(UUID applicationId) {
		return verifications.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream().map(VerificationService::attempt).toList();
	}

	@Override
	@Transactional
	public AttemptView decide(UUID applicationId, UUID verificationId, boolean accept, UUID staffId) {
		Verification attempt = verifications.findByIdAndApplicationId(verificationId, applicationId)
			.orElseThrow(() -> new NotFoundException("VERIFICATION_NOT_FOUND", "Verification not found"));
		if (!attempt.isAwaitingReview()) {
			throw new in.eko.eloka.shared.error.ConflictException("NOT_IN_MANUAL_REVIEW",
					"Only results waiting for a reviewer can be decided");
		}
		attempt.decide(accept, staffId);
		audit.record(new AuditEntry("VERIFICATION_DECIDED", "STAFF", staffId.toString(), IdentityApi.PLATFORM_ORG_ID,
				"ONBOARDING_APPLICATION", applicationId.toString(),
				Map.of("kind", attempt.getKind(), "decision", accept ? "ACCEPT" : "REJECT")));
		return attempt(attempt);
	}

	@Override
	@Transactional(readOnly = true)
	public String revealAccountNumber(UUID applicationId, String bankAccountId) {
		BankAccount account = bankAccounts.findByPublicIdAndApplicationId(bankAccountId, applicationId)
			.orElseThrow(() -> new NotFoundException("BANK_ACCOUNT_NOT_FOUND", "Bank account not found"));
		return cipher.decrypt(account.getAccountEnc());
	}

	@Override
	@Transactional(readOnly = true)
	public long bankAccountReuseCount(UUID applicationId) {
		return bankAccounts.countReuse(applicationId);
	}

	private static AttemptView attempt(Verification v) {
		return new AttemptView(v.getId(), v.getKind(), v.getStatus(), v.getNameMatchScore(), v.getFailureCode(),
				v.getResult(), v.getCreatedAt());
	}

	private KycResult kycView(Verification attempt) {
		if ("FAILED".equals(attempt.getStatus())) {
			return new KycResult(Status.FAILED, null, null, 0, attempt.getFailureCode());
		}
		var result = json.readTree(attempt.getResult());
		return new KycResult(status(attempt), result.get("name").asString(), result.get("aadhaar_masked").asString(),
				attempt.getNameMatchScore() == null ? 0 : attempt.getNameMatchScore(), null);
	}

	private BankResult bankView(BankAccount account, Verification attempt) {
		return new BankResult(status(attempt), account.getPublicId(), account.getAccountMasked(), account.getIfsc(),
				account.getBeneficiaryName(), attempt.getNameMatchScore() == null ? 0 : attempt.getNameMatchScore(),
				attempt.getFailureCode());
	}

	private static Status status(Verification attempt) {
		return switch (attempt.getStatus()) {
			case "SUCCEEDED" -> Status.SUCCEEDED;
			case "MANUAL_REVIEW" -> Status.MANUAL_REVIEW;
			case "FAILED" -> Status.FAILED;
			default -> Status.PENDING;
		};
	}

	private void record(Verification attempt, String outcome) {
		audit.record(new AuditEntry("VERIFICATION_" + attempt.getKind(), "APPLICANT", null, IdentityApi.PLATFORM_ORG_ID,
				"ONBOARDING_APPLICATION", attempt.getApplicationId().toString(), Map.of("outcome", outcome)));
	}
}
