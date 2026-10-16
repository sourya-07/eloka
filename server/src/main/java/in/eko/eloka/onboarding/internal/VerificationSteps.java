package in.eko.eloka.onboarding.internal;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import in.eko.eloka.agreement.AgreementApi;
import in.eko.eloka.agreement.AgreementContent;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.onboarding.ApplicationStatus;
import in.eko.eloka.onboarding.OnboardingProperties;
import in.eko.eloka.onboarding.StepKey;
import in.eko.eloka.onboarding.StepStatus;
import in.eko.eloka.shared.crypto.BlindIndex;
import in.eko.eloka.shared.crypto.FieldCipher;
import in.eko.eloka.shared.crypto.Masking;
import in.eko.eloka.shared.error.ApiProblem;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.verification.NameMatch;
import in.eko.eloka.verification.VerificationApi;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * The steps completed by their own resources (A12–A18, A27–A29): PAN, email, signatory KYC,
 * bank account and the agreement. Each call is one {@link ApplicationService#write} so the step
 * rules and the row lock apply; failed attempts are committed (they count towards blocking).
 */
@Service
class VerificationSteps {

	private static final Pattern PAN = Pattern.compile("^[A-Z]{5}\\d{4}[A-Z]$");
	private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
	private static final Pattern ACCOUNT = Pattern.compile("^\\d{9,18}$");
	private static final Duration EMAIL_REF_TTL = Duration.ofMinutes(10);

	/** The outcome of a verification call plus the snapshot to send back. */
	record Outcome(VerificationApi.Status status, Map<String, Object> body, String failureCode) {
	}

	private final ApplicationService applications;
	private final OnboardingApplicationRepository applicationRows;
	private final ApplicationDocuments documents;
	private final VerificationApi verification;
	private final AgreementApi agreements;
	private final IdentityApi identity;
	private final OnboardingProperties properties;
	private final FieldCipher cipher;
	private final BlindIndex index;
	private final JsonMapper json;
	private final Clock clock;

	VerificationSteps(ApplicationService applications, OnboardingApplicationRepository applicationRows,
			ApplicationDocuments documents, VerificationApi verification, AgreementApi agreements, IdentityApi identity,
			OnboardingProperties properties, FieldCipher cipher, BlindIndex index, JsonMapper json, Clock clock) {
		this.applications = applications;
		this.applicationRows = applicationRows;
		this.documents = documents;
		this.verification = verification;
		this.agreements = agreements;
		this.identity = identity;
		this.properties = properties;
		this.cipher = cipher;
		this.index = index;
		this.json = json;
		this.clock = clock;
	}

	/** A12: business PAN. Duplicate live PAN → 409; holder type must fit the entity type (OQ-B5). */
	Outcome verifyPan(UUID applicantId, String publicId, long version, String rawPan) {
		String pan = rawPan == null ? "" : rawPan.trim().toUpperCase(java.util.Locale.ROOT);
		if (!PAN.matcher(pan).matches()) {
			throw ValidationException.field("pan", "Enter a valid PAN (for example ABCDE1234F)");
		}
		return applications.write(applicantId, publicId, version, null, context -> {
			context.requirePrerequisites(StepKey.BUSINESS_PAN);
			requireAttemptsLeft(context, StepKey.BUSINESS_PAN);
			String holderType = pan.substring(3, 4);
			List<String> allowed = properties.panHolderTypes()
				.getOrDefault(context.application().getEntityType(), List.of());
			if (!allowed.contains(holderType)) {
				throw new ApiProblem(HttpStatus.UNPROCESSABLE_CONTENT, "PAN_HOLDER_TYPE_NOT_ALLOWED",
						"This PAN does not belong to a business of the type you selected",
						Map.of("allowed_holder_types", allowed));
			}
			byte[] panHash = index.hash("pan", pan);
			if (applicationRows.existsLivePan(panHash, context.applicationId(),
					EnumSet.of(ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN, ApplicationStatus.EXPIRED))) {
				throw new ApiProblem(HttpStatus.CONFLICT, "PAN_ALREADY_REGISTERED",
						"This PAN is already used by another application or partner");
			}
			String hintName = draftValue(context, StepKey.BUSINESS_DETAILS, "name");
			VerificationApi.PanResult result = verification.verifyPan(context.applicationId(), pan, hintName);
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("verification_id", "ver_" + result.verificationId());
			body.put("status", result.status().name());
			body.put("pan_masked", result.panMasked());
			switch (result.status()) {
				case SUCCEEDED, MANUAL_REVIEW -> {
					context.profile().pan(cipher.encrypt(pan), panHash, result.panMasked(), result.holderType(),
							result.nameAsPerPan());
					context.application().bindPan(panHash);
					Map<String, Object> summary = new LinkedHashMap<>();
					summary.put("pan_masked", result.panMasked());
					summary.put("name_as_per_pan", result.nameAsPerPan());
					summary.put("holder_type", result.holderType());
					context.complete(StepKey.BUSINESS_PAN, json.writeValueAsString(summary));
					body.put("name_as_per_pan", result.nameAsPerPan());
					body.put("holder_type", result.holderType());
				}
				case PENDING -> context.awaitExternal(StepKey.BUSINESS_PAN);
				case FAILED -> context.fail(StepKey.BUSINESS_PAN, result.failureCode());
			}
			return new Outcome(result.status(), body, result.failureCode());
		});
	}

	/** A13: sends an OTP to the email; the returned id carries the address (encrypted, 10 minutes). */
	Map<String, Object> startEmail(UUID applicantId, String publicId, String email, String clientIp) {
		String normalized = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
		if (!normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") || normalized.length() > 254) {
			throw ValidationException.field("email", "Enter a valid email address");
		}
		UUID applicationId = applications.write(applicantId, publicId, null, null, context -> {
			context.requirePrerequisites(StepKey.EMAIL_VERIFICATION);
			context.step(StepKey.EMAIL_VERIFICATION).saveDraft("{}");
			return context.applicationId();
		});
		identity.sendEmailOtp(IdentityApi.PLATFORM_ORG_ID, normalized, clientIp);
		String ref = applicationId + "|" + normalized + "|" + Instant.now(clock).getEpochSecond();
		String verificationId = "ver_" + Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(cipher.encryptBytes(ref.getBytes(StandardCharsets.UTF_8)));
		return Map.of("verification_id", verificationId, "expires_in", EMAIL_REF_TTL.toSeconds());
	}

	/** A14: confirms the email OTP and completes the step. */
	void confirmEmail(UUID applicantId, String publicId, String verificationId, String otp) {
		String[] parts;
		try {
			parts = new String(cipher.decryptBytes(Base64.getUrlDecoder().decode(verificationId.substring(4))),
					StandardCharsets.UTF_8).split("\\|");
		}
		catch (RuntimeException ex) {
			throw new in.eko.eloka.shared.error.NotFoundException("VERIFICATION_NOT_FOUND", "Request a new code");
		}
		UUID applicationId = applications.find(applicantId, publicId).getId();
		if (parts.length != 3 || !parts[0].equals(applicationId.toString())) {
			throw new in.eko.eloka.shared.error.NotFoundException("VERIFICATION_NOT_FOUND", "Request a new code");
		}
		if (Instant.ofEpochSecond(Long.parseLong(parts[2])).plus(EMAIL_REF_TTL).isBefore(Instant.now(clock))) {
			throw new ApiProblem(HttpStatus.GONE, "OTP_EXPIRED", "This code has expired. Please request a new one.");
		}
		String email = parts[1];
		if (!identity.verifyEmailOtp(IdentityApi.PLATFORM_ORG_ID, email, otp)) {
			throw new ApiProblem(HttpStatus.UNAUTHORIZED, "OTP_INVALID", "The code is not correct");
		}
		applications.write(applicantId, publicId, null, null, context -> {
			context.profile().email(cipher.encrypt(email), index.hash("email", email), Masking.email(email));
			context.complete(StepKey.EMAIL_VERIFICATION, json.writeValueAsString(Map.of("email_masked", Masking.email(email))));
			return null;
		});
	}

	/** A15: starts DigiLocker for the signatory (OQ-C1: DigiLocker; the Aadhaar number is never stored). */
	Map<String, Object> startKyc(UUID applicantId, String publicId, long version, String method, String publicBaseUrl) {
		if (!"DIGILOCKER".equals(method)) {
			throw ValidationException.field("method", "Only DIGILOCKER is supported");
		}
		return applications.write(applicantId, publicId, version, null, context -> {
			context.requirePrerequisites(StepKey.SIGNATORY_KYC);
			requireAttemptsLeft(context, StepKey.SIGNATORY_KYC);
			VerificationApi.KycSession session = verification.startKyc(context.applicationId(), publicBaseUrl);
			context.awaitExternal(StepKey.SIGNATORY_KYC);
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("session_id", session.sessionId());
			body.put("redirect_url", session.redirectUrl());
			return body;
		});
	}

	/** A16: reads the DigiLocker result and completes the step (name mismatch → flagged, OQ-C3). */
	Map<String, Object> kycStatus(UUID applicantId, String publicId, String sessionId) {
		OnboardingApplication application = applications.find(applicantId, publicId);
		if (!application.getStatus().isEditableByApplicant()) {
			VerificationApi.KycResult result = verification.kycStatus(application.getId(), sessionId, null);
			return kycBody(result);
		}
		return applications.write(applicantId, publicId, null, null, context -> {
			VerificationApi.KycResult result = verification.kycStatus(context.applicationId(), sessionId,
					context.profile().getSignatoryName());
			OnboardingStep step = context.step(StepKey.SIGNATORY_KYC);
			switch (result.status()) {
				case SUCCEEDED, MANUAL_REVIEW -> {
					if (step.getStatus() != StepStatus.LOCKED) {
						context.profile().kyc(result.name(), result.aadhaarMasked());
						Map<String, Object> summary = new LinkedHashMap<>();
						summary.put("name", result.name());
						summary.put("aadhaar_masked", result.aadhaarMasked());
						summary.put("name_matches_signatory", result.nameMatchScore() >= NameMatch.MATCH_THRESHOLD);
						context.complete(StepKey.SIGNATORY_KYC, json.writeValueAsString(summary));
					}
				}
				case FAILED -> context.fail(StepKey.SIGNATORY_KYC, result.failureCode());
				case PENDING -> {
				}
			}
			return kycBody(result);
		});
	}

	/** A17: bank account + penny-drop (OQ-P6: mandatory). Beneficiary name mismatch is flagged, not failed. */
	Outcome addBankAccount(UUID applicantId, String publicId, long version, String bankCode, String bankId, String ifscRaw,
			String account, String proofDocumentId) {
		StepData d = new StepData(null);
		String ifsc = ifscRaw == null ? null : ifscRaw.trim().toUpperCase(java.util.Locale.ROOT);
		if (bankCode == null || bankCode.isBlank()) {
			d.error("bank_code", "Required");
		}
		if (ifsc == null || !IFSC.matcher(ifsc).matches()) {
			d.error("ifsc", "Enter a valid IFSC (for example SBIN0001234)");
		}
		if (account == null || !ACCOUNT.matcher(account.trim()).matches()) {
			d.error("account", "Enter 9 to 18 digits");
		}
		if (proofDocumentId == null || !proofDocumentId.startsWith("doc_")) {
			d.error("proof_document_id", "Upload a cancelled cheque or bank statement first");
		}
		d.validate();
		return applications.write(applicantId, publicId, version, null, context -> {
			context.requirePrerequisites(StepKey.BANK_ACCOUNT);
			requireAttemptsLeft(context, StepKey.BANK_ACCOUNT);
			ApplicationDocument proof = documents.requireCurrent(context.applicationId(), proofDocumentId, "BANK_PROOF");
			BusinessProfile profile = context.profile();
			String expectedName = profile.getNameAsPerPan() != null ? profile.getNameAsPerPan() : profile.getLegalName();
			VerificationApi.BankResult result = verification.verifyBank(context.applicationId(),
					new VerificationApi.BankAccountInput(bankCode.trim(), bankId, ifsc, account.trim(), proof.getStoredDocumentId()),
					expectedName);
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("bank_account_id", result.bankAccountId());
			body.put("status", result.status().name());
			body.put("account_masked", result.accountMasked());
			switch (result.status()) {
				case SUCCEEDED, MANUAL_REVIEW -> {
					Map<String, Object> summary = new LinkedHashMap<>();
					summary.put("bank_account_id", result.bankAccountId());
					summary.put("account_masked", result.accountMasked());
					summary.put("ifsc", result.ifsc());
					summary.put("beneficiary_name", result.beneficiaryName());
					summary.put("name_matches", result.status() == VerificationApi.Status.SUCCEEDED);
					context.complete(StepKey.BANK_ACCOUNT, json.writeValueAsString(summary));
					body.put("beneficiary_name_returned", result.beneficiaryName());
					body.put("name_match_score", result.nameMatchScore());
				}
				case FAILED -> context.fail(StepKey.BANK_ACCOUNT, result.failureCode());
				case PENDING -> context.awaitExternal(StepKey.BANK_ACCOUNT);
			}
			return new Outcome(result.status(), body, result.failureCode());
		});
	}

	/** A27: renders the agreement from the verified data (same content → same agreement). */
	AgreementApi.AgreementView prepareAgreement(UUID applicantId, String publicId, long version) {
		return applications.write(applicantId, publicId, version, null, context -> {
			context.requirePrerequisites(StepKey.AGREEMENT_ESIGN);
			BusinessProfile p = context.profile();
			String address = String.join(", ", java.util.stream.Stream
				.of(p.getAddressLine1(), p.getAddressLine2(), p.getDistrict(), p.getState(), p.getPincode())
				.filter(part -> part != null && !part.isBlank())
				.toList());
			var entityType = context.application().getEntityType();
			AgreementApi.AgreementView view = agreements.prepare(context.applicationId(), applicantId,
					new AgreementContent(p.getLegalName(), entityType == null ? null : entityType.name(), p.getPanMasked(),
							p.getSignatoryName(), address, p.getEmailMasked()));
			if (context.step(StepKey.AGREEMENT_ESIGN).getStatus() == StepStatus.NOT_STARTED) {
				context.step(StepKey.AGREEMENT_ESIGN).saveDraft("{}");
			}
			return view;
		});
	}

	/** A28: opens the e-sign session (the web layer checked the step-up token). */
	AgreementApi.EsignStart startEsign(UUID applicantId, String publicId, long version, String agreementId) {
		return applications.write(applicantId, publicId, version, null, context -> {
			context.requirePrerequisites(StepKey.AGREEMENT_ESIGN);
			AgreementApi.EsignStart start = agreements.startEsign(context.applicationId(), agreementId, applicantId);
			if (context.step(StepKey.AGREEMENT_ESIGN).getStatus() != StepStatus.PENDING_EXTERNAL) {
				context.awaitExternal(StepKey.AGREEMENT_ESIGN);
			}
			return start;
		});
	}

	/**
	 * A29: asks the provider; once {@code SIGNED} (server-confirmed) the step completes and the
	 * application submits itself (T3) when nothing else is missing.
	 */
	AgreementApi.AgreementView agreementStatus(UUID applicantId, String publicId, String agreementId) {
		OnboardingApplication application = applications.find(applicantId, publicId);
		if (!application.getStatus().isEditableByApplicant()) {
			return agreements.status(application.getId(), agreementId);
		}
		return applications.write(applicantId, publicId, null, null, context -> {
			AgreementApi.AgreementView view = agreements.status(context.applicationId(), agreementId);
			if (view.isSigned()) {
				Map<String, Object> summary = new LinkedHashMap<>();
				summary.put("agreement_id", view.agreementId());
				summary.put("version", view.versionNo());
				summary.put("content_sha256", view.contentSha256());
				context.complete(StepKey.AGREEMENT_ESIGN, json.writeValueAsString(summary));
				applications.submitIfComplete(context, false);
			}
			return view;
		});
	}

	private static void requireAttemptsLeft(StepContext context, StepKey key) {
		if (context.step(key).getStatus() == StepStatus.BLOCKED) {
			throw new ApiProblem(HttpStatus.LOCKED, "STEP_BLOCKED",
					"Too many failed attempts on this step. Please contact support.");
		}
	}

	private String draftValue(StepContext context, StepKey key, String field) {
		String draft = context.step(key).getDraftData();
		if (draft == null) {
			return null;
		}
		var value = json.readTree(draft).get(field);
		return value == null || value.isNull() ? null : value.asString();
	}

	private static Map<String, Object> kycBody(VerificationApi.KycResult result) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", result.status().name());
		if (result.name() != null) {
			Map<String, Object> detail = new LinkedHashMap<>();
			detail.put("name", result.name());
			detail.put("aadhaar_masked", result.aadhaarMasked());
			detail.put("name_match_score", result.nameMatchScore());
			body.put("result", detail);
		}
		return body;
	}
}
