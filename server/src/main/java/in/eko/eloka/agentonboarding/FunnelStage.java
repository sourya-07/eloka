package in.eko.eloka.agentonboarding;

import java.util.List;

/**
 * Groups of pending steps shown as cards on the admin "Onboarding" dashboard. The id is the
 * EPS-style account status id the client sends back as a filter.
 */
public enum FunnelStage {

	IDENTITY(10, "Identity KYC Pending", List.of(KycStep.AADHAAR, KycStep.DIGILOCKER, KycStep.PAN, KycStep.SELFIE)),
	BUSINESS(11, "Business & Bank Pending", List.of(KycStep.BUSINESS, KycStep.BANK)),
	ACTIVATION(12, "PIN & Agreement Pending", List.of(KycStep.SECRET_PIN, KycStep.AGREEMENT));

	private final int id;
	private final String label;
	private final List<KycStep> steps;

	FunnelStage(int id, String label, List<KycStep> steps) {
		this.id = id;
		this.label = label;
		this.steps = steps;
	}

	public int id() {
		return id;
	}

	public String label() {
		return label;
	}

	public static FunnelStage of(KycStep current) {
		for (FunnelStage stage : values()) {
			if (stage.steps.contains(current)) {
				return stage;
			}
		}
		throw new IllegalArgumentException("No stage for " + current);
	}
}
