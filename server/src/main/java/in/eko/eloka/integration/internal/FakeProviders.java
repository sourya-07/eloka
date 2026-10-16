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
		public String unsafeReason() {
			return "FakeDigiLocker approves every Aadhaar consent";
		}
	}

	@Component
	@Profile({ "local", "test" })
	static class FakeEsign implements EsignProvider, UnsafeInProduction {

		@Override
		public EsignSession prepare(UUID userId, String agreementId) {
			return new EsignSession("FAKE-ESIGN-" + userId, true, null, 0);
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
}
