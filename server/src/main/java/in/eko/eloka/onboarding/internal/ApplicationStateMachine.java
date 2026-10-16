package in.eko.eloka.onboarding.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.onboarding.ApplicationClosed;
import in.eko.eloka.onboarding.ApplicationStatus;
import in.eko.eloka.shared.error.ApiProblem;
import in.eko.eloka.whitelabel.WhiteLabelApi;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The only way an application changes status. Callers hold the row lock; this checks the
 * {@code (from, event)} pair, moves the status and records the change in the audit log and the
 * applicant timeline in the same transaction. Guards (e.g. "all steps done") belong to the caller.
 */
@Component
class ApplicationStateMachine {

	private record Key(ApplicationStatus from, LifecycleEvent event) {
	}

	private static final Map<Key, ApplicationStatus> TRANSITIONS = Map.ofEntries(
			Map.entry(new Key(ApplicationStatus.IN_PROGRESS, LifecycleEvent.SUBMIT), ApplicationStatus.SUBMITTED),
			Map.entry(new Key(ApplicationStatus.CORRECTION_REQUIRED, LifecycleEvent.SUBMIT), ApplicationStatus.SUBMITTED),
			Map.entry(new Key(ApplicationStatus.IN_PROGRESS, LifecycleEvent.WITHDRAW), ApplicationStatus.WITHDRAWN),
			Map.entry(new Key(ApplicationStatus.SUBMITTED, LifecycleEvent.WITHDRAW), ApplicationStatus.WITHDRAWN),
			Map.entry(new Key(ApplicationStatus.CORRECTION_REQUIRED, LifecycleEvent.WITHDRAW), ApplicationStatus.WITHDRAWN),
			Map.entry(new Key(ApplicationStatus.IN_PROGRESS, LifecycleEvent.EXPIRE), ApplicationStatus.EXPIRED),
			Map.entry(new Key(ApplicationStatus.CORRECTION_REQUIRED, LifecycleEvent.EXPIRE), ApplicationStatus.EXPIRED),
			Map.entry(new Key(ApplicationStatus.SUBMITTED, LifecycleEvent.CLAIM), ApplicationStatus.UNDER_REVIEW),
			Map.entry(new Key(ApplicationStatus.UNDER_REVIEW, LifecycleEvent.RELEASE), ApplicationStatus.SUBMITTED),
			Map.entry(new Key(ApplicationStatus.UNDER_REVIEW, LifecycleEvent.REQUEST_CORRECTION), ApplicationStatus.CORRECTION_REQUIRED),
			Map.entry(new Key(ApplicationStatus.UNDER_REVIEW, LifecycleEvent.APPROVE), ApplicationStatus.APPROVED),
			Map.entry(new Key(ApplicationStatus.UNDER_REVIEW, LifecycleEvent.REJECT), ApplicationStatus.REJECTED),
			Map.entry(new Key(ApplicationStatus.ON_HOLD, LifecycleEvent.REJECT), ApplicationStatus.REJECTED),
			Map.entry(new Key(ApplicationStatus.UNDER_REVIEW, LifecycleEvent.HOLD), ApplicationStatus.ON_HOLD),
			Map.entry(new Key(ApplicationStatus.ON_HOLD, LifecycleEvent.RELEASE_HOLD), ApplicationStatus.UNDER_REVIEW));

	private static final Set<ApplicationStatus> CLOSED_WITHOUT_TENANT = Set.of(ApplicationStatus.WITHDRAWN,
			ApplicationStatus.EXPIRED, ApplicationStatus.REJECTED);

	private final TimelineEntryRepository timeline;
	private final AuditLog audit;
	private final ApplicationEventPublisher events;
	private final WhiteLabelApi whiteLabel;
	private final Clock clock;

	ApplicationStateMachine(TimelineEntryRepository timeline, AuditLog audit, ApplicationEventPublisher events,
			WhiteLabelApi whiteLabel, Clock clock) {
		this.timeline = timeline;
		this.audit = audit;
		this.events = events;
		this.whiteLabel = whiteLabel;
		this.clock = clock;
	}

	/** @return true when {@code event} is allowed from the current status */
	boolean allows(OnboardingApplication application, LifecycleEvent event) {
		return TRANSITIONS.containsKey(new Key(application.getStatus(), event));
	}

	ApplicationStatus apply(OnboardingApplication application, LifecycleEvent event, String actor) {
		return apply(application, event, actor, null);
	}

	/**
	 * @param actorId staff id for staff actions (kept in the audit log, not in the applicant timeline)
	 */
	ApplicationStatus apply(OnboardingApplication application, LifecycleEvent event, String actor, String actorId) {
		ApplicationStatus from = application.getStatus();
		ApplicationStatus to = TRANSITIONS.get(new Key(from, event));
		if (to == null) {
			throw new ApiProblem(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
					"This is not possible while the application is " + from.name().toLowerCase().replace('_', ' '),
					Map.of("current_status", from.name()));
		}
		Instant now = Instant.now(clock);
		application.moveTo(to, now);
		timeline.save(new TimelineEntry(application.getId(), "STATUS_" + to.name(), null, from, to, actor, now));
		audit.record(new AuditEntry("ONBOARDING_" + event.name(), actor, actorId, IdentityApi.PLATFORM_ORG_ID,
				"ONBOARDING_APPLICATION", application.getPublicId(), Map.of("from", from.name(), "to", to.name())));
		if (CLOSED_WITHOUT_TENANT.contains(to)) {
			// Give back what the application holds, in the same transaction
			whiteLabel.release(application.getId());
			events.publishEvent(new ApplicationClosed(application.getId(), to));
		}
		if (to == ApplicationStatus.APPROVED) {
			events.publishEvent(new in.eko.eloka.onboarding.ApplicationApproved(application.getId(),
					application.getPublicId(), actorId == null ? null : java.util.UUID.fromString(actorId)));
		}
		return to;
	}
}
