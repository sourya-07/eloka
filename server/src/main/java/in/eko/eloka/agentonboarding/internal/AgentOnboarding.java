package in.eko.eloka.agentonboarding.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import in.eko.eloka.agentonboarding.KycStep;
import in.eko.eloka.agentonboarding.OnboardingStatus;
import in.eko.eloka.shared.error.ConflictException;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** A user's onboarding plan and progress. Steps are completed only through {@link #complete}. */
@Entity
@Table(name = "agent_onboarding")
class AgentOnboarding extends BaseEntity {

	@Column(name = "org_id", nullable = false)
	private long orgId;

	@Column(name = "user_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
	private UUID userId;

	@Column(name = "completed_at")
	private Instant completedAt;

	@OneToMany(mappedBy = "onboarding", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<AgentOnboardingStep> steps = new ArrayList<>();

	protected AgentOnboarding() {
	}

	AgentOnboarding(long orgId, UUID userId) {
		this.orgId = orgId;
		this.userId = userId;
	}

	/** Adds any plan steps the user does not have yet (keeps finished ones). */
	void plan(List<KycStep> plan) {
		for (int i = 0; i < plan.size(); i++) {
			KycStep step = plan.get(i);
			if (steps.stream().noneMatch(s -> s.getStep() == step)) {
				steps.add(new AgentOnboardingStep(this, step, i));
			}
		}
		if (!pending().isEmpty()) {
			completedAt = null;
		}
	}

	void complete(KycStep step, Instant now) {
		AgentOnboardingStep row = steps.stream().filter(s -> s.getStep() == step).findFirst()
			.orElseThrow(() -> new ConflictException("STEP_NOT_IN_PLAN", "This step is not part of your onboarding"));
		row.complete(now);
		if (pending().isEmpty() && completedAt == null) {
			completedAt = now;
		}
	}

	boolean has(KycStep step) {
		return steps.stream().anyMatch(s -> s.getStep() == step);
	}

	List<KycStep> pending() {
		return ordered().stream().filter(s -> !s.isDone()).map(AgentOnboardingStep::getStep).toList();
	}

	OnboardingStatus status() {
		List<KycStep> plan = ordered().stream().map(AgentOnboardingStep::getStep).toList();
		List<KycStep> pending = pending();
		return pending.isEmpty() ? OnboardingStatus.DONE : new OnboardingStatus(plan, pending);
	}

	UUID getUserId() {
		return userId;
	}

	private List<AgentOnboardingStep> ordered() {
		return steps.stream().sorted(Comparator.comparingInt(AgentOnboardingStep::getSortOrder)).toList();
	}
}
