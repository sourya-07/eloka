package in.eko.eloka.onboarding;

import java.util.UUID;

/**
 * Published after the checker approves an application (T7), in the same transaction. P9
 * provisioning listens for it to create the tenant, its domain and the Local Admin.
 *
 * @param approvedBy staff id of the checker
 */
public record ApplicationApproved(UUID applicationId, String onboardingId, UUID approvedBy) {
}
