package in.eko.eloka.agentonboarding;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Agent onboarding. New retailers and distributors start with a KYC plan for their type;
 * each save method stores that step's data and marks it done. Users without a plan (created
 * before onboarding existed, or admins) count as fully onboarded.
 */
public interface AgentOnboardingApi {

	/** @return the user's progress; {@link OnboardingStatus#DONE} when there is no plan */
	OnboardingStatus status(long orgId, UUID userId);

	Map<UUID, OnboardingStatus> statuses(long orgId, Collection<UUID> userIds);

	/** Everyone in the org who has not finished onboarding. */
	Map<UUID, OnboardingStatus> inProgress(long orgId);

	/** Starts (or restarts, keeping done steps) the plan for a user type. Idempotent. */
	void start(long orgId, UUID userId, int userType);

	/** Marks steps done without data (seed data and admin overrides only). */
	void completeSteps(long orgId, UUID userId, Set<KycStep> steps);

	void saveAadhaarImages(OnboardingSubject subject, UUID frontDocumentId, UUID backDocumentId);

	/** @param pan validated and normalised by the caller's step (uppercase, 10 chars) */
	void savePan(OnboardingSubject subject, String pan, UUID documentId);

	void saveLocation(OnboardingSubject subject, String latlong, String shopType);

	void saveSelfie(OnboardingSubject subject, UUID documentId);

	void saveBusiness(OnboardingSubject subject, BusinessDetails details);

	void saveBankAccount(OnboardingSubject subject, BankAccountDetails account);

	/** Passbook / cheque image; completes the bank step (needs the account first). */
	void saveBankProof(OnboardingSubject subject, UUID documentId);

	void completeDigiLocker(OnboardingSubject subject, String requestId);

	void markPinSet(OnboardingSubject subject);

	void signAgreement(OnboardingSubject subject, String providerDocumentId);
}
