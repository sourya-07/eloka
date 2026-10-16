package in.eko.eloka.agentonboarding.internal;

import java.time.Instant;
import in.eko.eloka.agentonboarding.KycStep;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One step of a user's plan. */
@Entity
@Table(name = "agent_onboarding_step")
class AgentOnboardingStep extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "onboarding_id", nullable = false)
	private AgentOnboarding onboarding;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private KycStep step;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected AgentOnboardingStep() {
	}

	AgentOnboardingStep(AgentOnboarding onboarding, KycStep step, int sortOrder) {
		this.onboarding = onboarding;
		this.step = step;
		this.sortOrder = sortOrder;
	}

	KycStep getStep() {
		return step;
	}

	int getSortOrder() {
		return sortOrder;
	}

	boolean isDone() {
		return completedAt != null;
	}

	void complete(Instant now) {
		if (completedAt == null) {
			completedAt = now;
		}
	}
}
