package in.eko.eloka.whitelabel;

import java.util.Optional;
import java.util.UUID;

/** Public API of the white-label module: sub-domain availability and reservation, branding drafts. */
public interface WhiteLabelApi {

	/** A26: is {@code label} free? Includes a reason and suggestions when it is not. */
	SubdomainAvailability availability(String label);

	/**
	 * Reserves {@code label} for the application, releasing any other label it held. Atomic with
	 * the uniqueness check, so two applicants cannot both get it.
	 *
	 * @throws in.eko.eloka.shared.error.ConflictException {@code SUBDOMAIN_TAKEN} with suggestions
	 */
	SubdomainReservation reserve(UUID applicationId, String label);

	/** The application's current reservation, if any. */
	Optional<SubdomainReservation> reservationOf(UUID applicationId);

	/** Saves (replaces) the branding draft; published at activation (P9). */
	void saveBranding(UUID applicationId, BrandingDraftInput draft);

	/** Frees what the application holds (on withdraw, expiry, rejection). */
	void release(UUID applicationId);
}
