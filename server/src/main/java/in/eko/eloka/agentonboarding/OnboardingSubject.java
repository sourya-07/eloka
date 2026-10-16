package in.eko.eloka.agentonboarding;

import java.util.UUID;

/**
 * Whose onboarding a call works on, and who is doing it (the agent, or the distributor/admin
 * assisting them). The gateway resolves this from the session and checks the scope.
 */
public record OnboardingSubject(long orgId, UUID userId, UUID actorUserId) {
}
