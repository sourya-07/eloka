package in.eko.eloka.onboarding.internal;

import java.util.UUID;

/** Validated {@code BUSINESS_DETAILS} step data (snake_case field names are mapped in the web layer). */
record BusinessDetailsInput(String name, String companyType, String signatoryName, String contactMobile,
		String alternateMobile, String addressLine1, String addressLine2, String district, String state, String pincode,
		String gstin, String cin, UUID signatureDocumentId) {
}
