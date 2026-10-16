package in.eko.eloka.onboarding;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Step policy and limits for Local Admin onboarding ({@code eloka.onboarding}). Business rules
 * that are still open questions (docs/onboarding/OPEN_QUESTIONS.md) live here, not in code.
 *
 * @param idleExpiry an application with no applicant activity for this long expires (OQ-B9)
 * @param maxAttempts verification attempts per step before it is {@code BLOCKED}
 * @param livePhotoRequired whether the live photo step is mandatory (OQ-P4)
 * @param bankAccountRequired whether bank verification is mandatory (OQ-P6)
 * @param panHolderTypes allowed 4th PAN character per entity type (OQ-B5)
 * @param requiredDocuments document types required per entity type (OQ-P5)
 * @param consentVersion current privacy/consent text version the applicant must accept
 * @param termsVersion current terms version
 * @param declarationVersion current declaration text version
 * @param otpRefTtl how long an {@code otp_ref} from A1 can be used
 */
@ConfigurationProperties("eloka.onboarding")
public record OnboardingProperties(Duration idleExpiry, int maxAttempts, boolean livePhotoRequired,
		boolean bankAccountRequired, Map<EntityType, List<String>> panHolderTypes,
		Map<EntityType, List<String>> requiredDocuments, String consentVersion, String termsVersion,
		String declarationVersion, Duration otpRefTtl) {

	public OnboardingProperties {
		idleExpiry = idleExpiry == null ? Duration.ofDays(30) : idleExpiry;
		maxAttempts = maxAttempts == 0 ? 3 : maxAttempts;
		panHolderTypes = panHolderTypes == null || panHolderTypes.isEmpty()
				? Map.of(EntityType.PRIVATE_LIMITED, List.of("C"), EntityType.LLP, List.of("F"), EntityType.PARTNERSHIP,
						List.of("F"), EntityType.SOLE_PROPRIETORSHIP, List.of("P"))
				: Map.copyOf(panHolderTypes);
		requiredDocuments = requiredDocuments == null || requiredDocuments.isEmpty()
				? Map.of(EntityType.PRIVATE_LIMITED, List.of("ADDRESS_PROOF", "INCORPORATION_CERTIFICATE"), EntityType.LLP,
						List.of("ADDRESS_PROOF", "INCORPORATION_CERTIFICATE"), EntityType.PARTNERSHIP,
						List.of("ADDRESS_PROOF", "PARTNERSHIP_DEED"), EntityType.SOLE_PROPRIETORSHIP, List.of("ADDRESS_PROOF"))
				: Map.copyOf(requiredDocuments);
		consentVersion = consentVersion == null ? "2026-10-01" : consentVersion;
		termsVersion = termsVersion == null ? "2026-10-01" : termsVersion;
		declarationVersion = declarationVersion == null ? "2026-10-01" : declarationVersion;
		otpRefTtl = otpRefTtl == null ? Duration.ofMinutes(10) : otpRefTtl;
	}

	/** Steps that can be skipped for an entity type (everything else is required). */
	public boolean isRequired(StepKey step) {
		return switch (step) {
			case LIVE_PHOTO -> livePhotoRequired;
			case BANK_ACCOUNT -> bankAccountRequired;
			default -> true;
		};
	}
}
