package in.eko.eloka.agentonboarding.internal;

import java.util.List;
import java.util.Optional;
import in.eko.eloka.agentonboarding.KycStep;
import in.eko.eloka.network.UserTypes;

/** Which steps each user type must finish, in order. */
final class KycPlans {

	static final List<KycStep> RETAILER = List.of(KycStep.AADHAAR, KycStep.PAN, KycStep.SELFIE, KycStep.BUSINESS,
			KycStep.BANK, KycStep.SECRET_PIN, KycStep.AGREEMENT);

	static final List<KycStep> DISTRIBUTOR = List.of(KycStep.DIGILOCKER, KycStep.PAN, KycStep.BUSINESS, KycStep.BANK,
			KycStep.AGREEMENT);

	private KycPlans() {
	}

	/** @return the plan, or empty for types that do not onboard (admins, field executives) */
	static Optional<List<KycStep>> forType(int userType) {
		return switch (userType) {
			case UserTypes.MERCHANT, UserTypes.I_MERCHANT -> Optional.of(RETAILER);
			case UserTypes.DISTRIBUTOR, UserTypes.SUPER_DISTRIBUTOR -> Optional.of(DISTRIBUTOR);
			default -> Optional.empty();
		};
	}
}
