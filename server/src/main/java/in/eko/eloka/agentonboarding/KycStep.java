package in.eko.eloka.agentonboarding;

/**
 * Onboarding steps the client knows (features/onboarding/constants.ts), with the "role"
 * id the client uses to match them and the label it shows.
 */
public enum KycStep {

	AADHAAR(12400, "Aadhaar Verification"),
	DIGILOCKER(24000, "Digilocker Verification"),
	PAN(12300, "PAN Verification"),
	SELFIE(12500, "Selfie KYC"),
	BUSINESS(13300, "Business Details"),
	BANK(51700, "Add Bank Account"),
	SECRET_PIN(12600, "Secret Pin"),
	AGREEMENT(12800, "Sign Agreement");

	private final int role;
	private final String label;

	KycStep(int role, String label) {
		this.role = role;
		this.label = label;
	}

	public int role() {
		return role;
	}

	public String label() {
		return label;
	}
}
