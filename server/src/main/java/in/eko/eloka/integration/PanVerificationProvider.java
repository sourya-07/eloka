package in.eko.eloka.integration;

/** Checks a PAN with the Income Tax database (through a licensed provider). */
public interface PanVerificationProvider {

	/**
	 * @param hintName the name the applicant gave; real providers ignore it, fakes echo it so local
	 *        demos produce a matching result
	 * @throws in.eko.eloka.shared.error.ConflictException {@code PROVIDER_NOT_CONFIGURED} outside local/test
	 */
	PanCheck verify(String pan, String hintName);

	enum Outcome {
		VALID,
		INVALID,
		/** The provider will answer later; ask again. */
		PENDING
	}

	/**
	 * @param nameAsPerPan registered name (VALID only)
	 * @param holderType 4th PAN character: C company, F firm/LLP, P person, …
	 */
	record PanCheck(Outcome outcome, String nameAsPerPan, String holderType) {
	}
}
