package in.eko.eloka.integration.internal;

import java.util.UUID;
import in.eko.eloka.integration.DigiLockerProvider;
import in.eko.eloka.integration.EsignProvider;
import in.eko.eloka.shared.error.ConflictException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Staging/production until a provider contract exists: the steps fail with a clear message
 * instead of pretending.
 */
final class UnconfiguredProviders {

	private UnconfiguredProviders() {
	}

	@Component
	@Profile("!local & !test")
	static class NoDigiLocker implements DigiLockerProvider {

		@Override
		public DigiLockerSession start(UUID userId, String publicBaseUrl) {
			throw new ConflictException("PROVIDER_NOT_CONFIGURED", "DigiLocker verification is not available yet.");
		}

		@Override
		public boolean isCompleted(String requestId) {
			return false;
		}
	}

	@Component
	@Profile("!local & !test")
	static class NoEsign implements EsignProvider {

		@Override
		public EsignSession prepare(UUID userId, String agreementId) {
			throw new ConflictException("PROVIDER_NOT_CONFIGURED", "Agreement e-sign is not available yet.");
		}

		@Override
		public boolean isSigned(String documentId) {
			return false;
		}
	}
}
