package in.eko.eloka.notification;

import java.time.Instant;

/**
 * One in-app notification for one user.
 *
 * @param type {@code NORMAL} or {@code AD} (shown as a banner/popup by the client)
 * @param read 0 unread, 1 read, 2 dismissed
 * @param delivered whether the client has acknowledged receiving it
 */
public record InboxItem(long id, String type, String title, String body, String imageUrl, String link,
		String linkLabel, int priority, Instant notifyTime, int read, boolean delivered) {
}
