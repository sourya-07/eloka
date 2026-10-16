package in.eko.eloka.onboarding;

import java.util.List;

/**
 * The steps of a Local Admin application in recommended order, with their prerequisites
 * (docs/onboarding/ONBOARDING_STATE_MACHINE.md §4.1). The backend enforces only prerequisites;
 * the order is a recommendation for the UI.
 *
 * <p>Change from the blueprint: {@code PROGRAM_SELECTION} also needs {@code CONSENT}, so no
 * business data is collected before the applicant has agreed to its processing (DPDP Act).
 */
public enum StepKey {
	MOBILE_VERIFICATION,
	CONSENT,
	PROGRAM_SELECTION,
	BUSINESS_PAN,
	BUSINESS_DETAILS,
	EMAIL_VERIFICATION,
	SIGNATORY_KYC,
	LIVE_PHOTO,
	DOCUMENTS,
	BANK_ACCOUNT,
	WHITE_LABEL_SETUP,
	DECLARATION,
	AGREEMENT_ESIGN;

	/** Steps that must be satisfied before this one can be started. */
	public List<StepKey> prerequisites() {
		return switch (this) {
			case MOBILE_VERIFICATION -> List.of();
			case CONSENT, EMAIL_VERIFICATION -> List.of(MOBILE_VERIFICATION);
			case PROGRAM_SELECTION -> List.of(MOBILE_VERIFICATION, CONSENT);
			case BUSINESS_PAN, WHITE_LABEL_SETUP -> List.of(PROGRAM_SELECTION);
			case BUSINESS_DETAILS -> List.of(BUSINESS_PAN);
			case SIGNATORY_KYC, DOCUMENTS, BANK_ACCOUNT -> List.of(BUSINESS_DETAILS);
			case LIVE_PHOTO -> List.of(SIGNATORY_KYC);
			case DECLARATION -> List.of(CONSENT, PROGRAM_SELECTION, BUSINESS_PAN, BUSINESS_DETAILS, EMAIL_VERIFICATION,
					SIGNATORY_KYC, LIVE_PHOTO, DOCUMENTS, BANK_ACCOUNT, WHITE_LABEL_SETUP);
			case AGREEMENT_ESIGN -> List.of(DECLARATION);
		};
	}

	/**
	 * Steps whose result is no longer valid when this step is completed again with new data
	 * (§4.3). They go back to {@code IN_PROGRESS}, and so do the steps they in turn invalidate.
	 */
	public List<StepKey> invalidates() {
		return switch (this) {
			case BUSINESS_PAN -> List.of(BUSINESS_DETAILS, BANK_ACCOUNT, DECLARATION, AGREEMENT_ESIGN);
			case BUSINESS_DETAILS -> List.of(DECLARATION, AGREEMENT_ESIGN);
			case SIGNATORY_KYC -> List.of(LIVE_PHOTO, DECLARATION, AGREEMENT_ESIGN);
			// The entity type decides the allowed PAN holder types and the required documents
			case PROGRAM_SELECTION -> List.of(BUSINESS_PAN, DOCUMENTS, DECLARATION, AGREEMENT_ESIGN);
			case CONSENT, EMAIL_VERIFICATION, LIVE_PHOTO, DOCUMENTS, BANK_ACCOUNT, WHITE_LABEL_SETUP ->
				List.of(DECLARATION, AGREEMENT_ESIGN);
			case DECLARATION -> List.of(AGREEMENT_ESIGN);
			case MOBILE_VERIFICATION, AGREEMENT_ESIGN -> List.of();
		};
	}
}
