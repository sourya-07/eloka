package in.eko.eloka.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * An in-app notification to publish.
 *
 * @param userId recipient, or {@code null} for everyone in the org
 */
public record NewNotification(long orgId, UUID userId, String type, String title, String body, String imageUrl,
		String link, String linkLabel, int priority, Instant notifyTime) {
}
