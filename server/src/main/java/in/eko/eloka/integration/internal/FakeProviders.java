package in.eko.eloka.integration.internal;

import java.util.UUID;
import in.eko.eloka.integration.DigiLockerProvider;
import in.eko.eloka.integration.EsignProvider;
import in.eko.eloka.shared.config.UnsafeInProduction;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Local/test stand-ins: every consent and signature succeeds. Refused in production. */
final class FakeProviders {

	private FakeProviders() {
	}

	@Component
	@Profile({ "local", "test" })
	static class FakeDigiLocker implements DigiLockerProvider, UnsafeInProduction {

		@Override
		public DigiLockerSession start(UUID userId, String publicBaseUrl) {
			String requestId = "FAKE-DL-" + UUID.randomUUID();
			return new DigiLockerSession(publicBaseUrl + "/v1/dev/digilocker?requestId=" + requestId, requestId);
		}

		@Override
		public boolean isCompleted(String requestId) {
			return requestId != null && requestId.startsWith("FAKE-DL-");
		}

		@Override
		public java.util.Optional<KycIdentity> identity(String requestId, String hintName) {
			return isCompleted(requestId)
					? java.util.Optional.of(new KycIdentity(hintName == null ? "DEMO SIGNATORY" : hintName, "XXXX-XXXX-1234"))
					: java.util.Optional.empty();
		}

		@Override
		public String unsafeReason() {
			return "FakeDigiLocker approves every Aadhaar consent";
		}
	}

	/**
	 * PAN rules for demos and tests (by the four digits): {@code 0000} is invalid, {@code 9999} is
	 * pending on the first call and valid afterwards, anything else is valid with the hinted name.
	 */
	@Component
	@Profile({ "local", "test" })
	static class FakePanVerification implements in.eko.eloka.integration.PanVerificationProvider, UnsafeInProduction {

		private final java.util.Set<String> asked = java.util.concurrent.ConcurrentHashMap.newKeySet();

		@Override
		public PanCheck verify(String pan, String hintName) {
			String digits = pan.substring(5, 9);
			if ("0000".equals(digits)) {
				return new PanCheck(Outcome.INVALID, null, null);
			}
			if ("9999".equals(digits) && asked.add(pan)) {
				return new PanCheck(Outcome.PENDING, null, null);
			}
			return new PanCheck(Outcome.VALID, hintName == null ? "DEMO ENTERPRISE PRIVATE LIMITED" : hintName.toUpperCase(),
					pan.substring(3, 4));
		}

		@Override
		public String unsafeReason() {
			return "FakePanVerification accepts every well-formed PAN";
		}
	}

	/** Bank rules for demos and tests: account ending 0000 is invalid, 1111 returns another holder name. */
	@Component
	@Profile({ "local", "test" })
	static class FakeBankAccountVerifier implements in.eko.eloka.integration.BankAccountVerifier, UnsafeInProduction {

		@Override
		public BankCheck verify(String ifsc, String accountNumber, String hintName) {
			if (accountNumber.endsWith("0000")) {
				return new BankCheck(false, null, "BANK_ACCOUNT_INVALID");
			}
			if (accountNumber.endsWith("1111")) {
				return new BankCheck(true, "SOMEONE ELSE", null);
			}
			return new BankCheck(true, hintName == null ? "DEMO ACCOUNT HOLDER" : hintName.toUpperCase(), null);
		}

		@Override
		public String unsafeReason() {
			return "FakeBankAccountVerifier accepts every bank account";
		}
	}

	@Component
	@Profile({ "local", "test" })
	static class FakeEsign implements EsignProvider, UnsafeInProduction {

		@Override
		public EsignSession prepare(UUID userId, String agreementId) {
			// unique per session (one provider document per agreement version); fits the 80-char agent column
			return new EsignSession("FAKE-ESIGN-" + UUID.randomUUID(), true, null, 0);
		}

		@Override
		public boolean isSigned(String documentId) {
			return documentId != null && documentId.startsWith("FAKE-ESIGN-");
		}

		@Override
		public String unsafeReason() {
			return "FakeEsign marks every agreement as signed";
		}
	}

	/**
	 * Tests only: accepts {@code test-google:<email>:<hosted domain>} instead of a signed Google token,
	 * so staff sign-in can be tested without Google. Local runs use the real verifier (or staff OTP).
	 */
	@Component
	@Profile("test")
	static class FakeGoogleIdentityVerifier implements in.eko.eloka.integration.GoogleIdentityVerifier, UnsafeInProduction {

		@Override
		public java.util.Optional<GoogleIdentity> verify(String idToken) {
			if (idToken == null || !idToken.startsWith("test-google:")) {
				return java.util.Optional.empty();
			}
			String[] parts = idToken.split(":");
			if (parts.length < 2 || parts[1].isBlank()) {
				return java.util.Optional.empty(); // malformed: refused like a bad Google token, never a crash
			}
			return java.util.Optional.of(new GoogleIdentity(parts[1], true, parts.length > 2 ? parts[2] : null, "Test Staff"));
		}

		@Override
		public String unsafeReason() {
			return "FakeGoogleIdentityVerifier accepts unsigned test tokens";
		}
	}
}

