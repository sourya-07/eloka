/**
 * Message templates, channel routing (SMS, email), de-duplication and delivery status.
 *
 * <p>Status: SMS port with a log-only sender (P1), in-app inbox (P2). Public API: {@link in.eko.eloka.notification.SmsSender},
 * {@link in.eko.eloka.notification.NotificationApi}.
 * Only types in this package (the module's public API) may be used by other modules;
 * sub-packages such as {@code internal} are private to the module.
 */
@ApplicationModule(displayName = "Notification")
package in.eko.eloka.notification;

import org.springframework.modulith.ApplicationModule;
