package in.eko.eloka.agentonboarding.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import in.eko.eloka.agentonboarding.AgentOnboardingApi;
import in.eko.eloka.agentonboarding.BankAccountDetails;
import in.eko.eloka.agentonboarding.BusinessDetails;
import in.eko.eloka.agentonboarding.KycStep;
import in.eko.eloka.agentonboarding.OnboardingStatus;
import in.eko.eloka.agentonboarding.OnboardingSubject;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.network.UserCreated;
import in.eko.eloka.shared.crypto.BlindIndex;
import in.eko.eloka.shared.crypto.FieldCipher;
import in.eko.eloka.shared.crypto.Masking;
import in.eko.eloka.shared.crypto.Mobiles;
import in.eko.eloka.shared.error.ConflictException;
import in.eko.eloka.shared.error.ValidationException;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class AgentOnboardingService implements AgentOnboardingApi {

	private final AgentOnboardingRepository onboardings;
	private final AgentKycRepository kycs;
	private final FieldCipher cipher;
	private final BlindIndex index;
	private final AuditLog audit;
	private final Clock clock;

	AgentOnboardingService(AgentOnboardingRepository onboardings, AgentKycRepository kycs, FieldCipher cipher,
			BlindIndex index, AuditLog audit, Clock clock) {
		this.onboardings = onboardings;
		this.kycs = kycs;
		this.cipher = cipher;
		this.index = index;
		this.audit = audit;
		this.clock = clock;
	}

	/** Every new retailer or distributor starts onboarding, in the same transaction as the user. */
	@EventListener
	@Transactional
	void onUserCreated(UserCreated event) {
		start(event.orgId(), event.userId(), event.userType());
	}

	@Override
	public OnboardingStatus status(long orgId, UUID userId) {
		return onboardings.findByOrgIdAndUserId(orgId, userId).map(AgentOnboarding::status).orElse(OnboardingStatus.DONE);
	}

	@Override
	public Map<UUID, OnboardingStatus> statuses(long orgId, Collection<UUID> userIds) {
		Map<UUID, OnboardingStatus> result = new HashMap<>();
		if (!userIds.isEmpty()) {
			onboardings.findByOrgIdAndUserIdIn(orgId, userIds).forEach(o -> result.put(o.getUserId(), o.status()));
		}
		return result;
	}

	@Override
	public Map<UUID, OnboardingStatus> inProgress(long orgId) {
		Map<UUID, OnboardingStatus> result = new HashMap<>();
		onboardings.findByOrgIdAndCompletedAtIsNull(orgId).forEach(o -> {
			OnboardingStatus status = o.status();
			if (status.inProgress()) {
				result.put(o.getUserId(), status);
			}
		});
		return result;
	}

	@Override
	@Transactional
	public void start(long orgId, UUID userId, int userType) {
		KycPlans.forType(userType).ifPresent(plan -> {
			AgentOnboarding onboarding = onboardings.findByOrgIdAndUserId(orgId, userId)
				.orElseGet(() -> onboardings.save(new AgentOnboarding(orgId, userId)));
			onboarding.plan(plan);
		});
	}

	@Override
	@Transactional
	public void completeSteps(long orgId, UUID userId, Set<KycStep> steps) {
		onboardings.findByOrgIdAndUserId(orgId, userId).ifPresent(o -> {
			Instant now = Instant.now(clock);
			steps.stream().filter(o::has).forEach(step -> o.complete(step, now));
		});
	}

	@Override
	@Transactional
	public void saveAadhaarImages(OnboardingSubject s, UUID front, UUID back) {
		if (front == null || back == null) {
			throw ValidationException.field("aadhaar", "Upload both sides of the Aadhaar card");
		}
		step(s, KycStep.AADHAAR, kyc -> kyc.aadhaarImages(front, back), true);
	}

	@Override
	@Transactional
	public void savePan(OnboardingSubject s, String pan, UUID documentId) {
		byte[] hash = index.hash("pan", pan);
		if (kycs.existsByOrgIdAndPanHashAndUserIdNot(s.orgId(), hash, s.userId())) {
			throw new ConflictException("PAN_ALREADY_REGISTERED", "This PAN is already registered with another account");
		}
		step(s, KycStep.PAN, kyc -> kyc.pan(cipher.encrypt(pan), hash, Masking.pan(pan), documentId), true);
	}

	@Override
	@Transactional
	public void saveLocation(OnboardingSubject s, String latlong, String shopType) {
		step(s, KycStep.SELFIE, kyc -> kyc.location(latlong), false);
	}

	@Override
	@Transactional
	public void saveSelfie(OnboardingSubject s, UUID documentId) {
		step(s, KycStep.SELFIE, kyc -> kyc.selfie(documentId), true);
	}

	@Override
	@Transactional
	public void saveBusiness(OnboardingSubject s, BusinessDetails d) {
		byte[] contact = d.contactPersonMobile() == null ? null : cipher.encrypt(Mobiles.normalize(d.contactPersonMobile()));
		byte[] alternate = d.alternateMobile() == null || d.alternateMobile().isBlank() ? null
				: cipher.encrypt(Mobiles.normalize(d.alternateMobile()));
		step(s, KycStep.BUSINESS, kyc -> kyc.business(d.companyType(), d.authorizedSignatory(), contact, alternate), true);
	}

	@Override
	@Transactional
	public void saveBankAccount(OnboardingSubject s, BankAccountDetails a) {
		String account = a.accountNumber() == null ? "" : a.accountNumber().replaceAll("\\s", "");
		if (!account.matches("\\d{9,18}")) {
			throw ValidationException.field("account", "Enter a valid account number (9 to 18 digits)");
		}
		String ifsc = a.ifsc() == null ? "" : a.ifsc().trim().toUpperCase();
		if (!ifsc.matches("[A-Z]{4}0[A-Z0-9]{6}")) {
			throw ValidationException.field("ifsc", "Enter a valid IFSC code");
		}
		step(s, KycStep.BANK, kyc -> kyc.bankAccount(a.bankCode(), a.bankId(), ifsc, cipher.encrypt(account),
				index.hash("bank_account", ifsc + ":" + account), Masking.account(account)), false);
	}

	@Override
	@Transactional
	public void saveBankProof(OnboardingSubject s, UUID documentId) {
		AgentKyc kyc = kycOf(s);
		if (!kyc.hasBankAccount()) {
			throw new ConflictException("BANK_ACCOUNT_MISSING", "Enter the bank account details first");
		}
		step(s, KycStep.BANK, k -> k.bankProof(documentId), true);
	}

	@Override
	@Transactional
	public void completeDigiLocker(OnboardingSubject s, String requestId) {
		step(s, KycStep.DIGILOCKER, kyc -> kyc.digiLocker(requestId), true);
	}

	@Override
	@Transactional
	public void markPinSet(OnboardingSubject s) {
		step(s, KycStep.SECRET_PIN, kyc -> {
		}, true);
	}

	@Override
	@Transactional
	public void signAgreement(OnboardingSubject s, String providerDocumentId) {
		Instant now = Instant.now(clock);
		step(s, KycStep.AGREEMENT, kyc -> kyc.agreement(providerDocumentId, now), true);
	}

	/** Stores data for a step of the subject's plan and, when {@code completes}, marks the step done. */
	private void step(OnboardingSubject s, KycStep step, Consumer<AgentKyc> update, boolean completes) {
		AgentOnboarding onboarding = onboardings.findByOrgIdAndUserId(s.orgId(), s.userId())
			.orElseThrow(() -> new ConflictException("NOT_ONBOARDING", "Onboarding is already complete"));
		if (!onboarding.has(step)) {
			throw new ConflictException("STEP_NOT_IN_PLAN", "This step is not part of your onboarding");
		}
		update.accept(kycOf(s));
		if (completes) {
			onboarding.complete(step, Instant.now(clock));
			audit.record(new AuditEntry("ONBOARDING_STEP_COMPLETED", "USER", s.actorUserId().toString(), s.orgId(), "USER",
					s.userId().toString(), Map.of("step", step.name(), "assisted",
							Boolean.toString(!s.actorUserId().equals(s.userId())))));
			if (onboarding.pending().isEmpty()) {
				audit.record(new AuditEntry("ONBOARDING_COMPLETED", "SYSTEM", null, s.orgId(), "USER", s.userId().toString(),
						Map.of()));
			}
		}
	}

	private AgentKyc kycOf(OnboardingSubject s) {
		return kycs.findByOrgIdAndUserId(s.orgId(), s.userId()).orElseGet(() -> kycs.save(new AgentKyc(s.orgId(), s.userId())));
	}
}
