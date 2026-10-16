package in.eko.eloka.agentonboarding;

import java.util.List;

/**
 * Where a user is in onboarding.
 *
 * @param plan all steps for the user's type, in order (empty when not onboarding)
 * @param pending steps still to do, in order
 */
public record OnboardingStatus(List<KycStep> plan, List<KycStep> pending) {

	public static final OnboardingStatus DONE = new OnboardingStatus(List.of(), List.of());

	public boolean inProgress() {
		return !pending.isEmpty();
	}

	/** The step the user is on, or null when done. */
	public KycStep current() {
		return pending.isEmpty() ? null : pending.getFirst();
	}
}
