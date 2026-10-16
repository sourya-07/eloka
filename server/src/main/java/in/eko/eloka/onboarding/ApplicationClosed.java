package in.eko.eloka.onboarding;

import java.util.UUID;

/**
 * Published when an application ends without becoming a tenant (withdrawn, expired, rejected).
 * The sub-domain is released directly by onboarding; this event is for later listeners
 * (notifications, retention timers) that must not be called synchronously.
 */
public record ApplicationClosed(UUID applicationId, ApplicationStatus finalStatus) {
}
