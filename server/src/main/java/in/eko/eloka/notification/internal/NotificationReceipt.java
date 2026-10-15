package in.eko.eloka.notification.internal;

import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Per-user state of a notification: delivered, read, dismissed. */
@Entity
@Table(name = "notification_receipt", uniqueConstraints = @UniqueConstraint(columnNames = { "notification_id", "user_id" }))
class NotificationReceipt extends BaseEntity {

	@Column(name = "notification_id", nullable = false)
	private long notificationId;

	@Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID userId;

	@Column(nullable = false)
	private boolean delivered;

	/** 0 unread, 1 read, 2 dismissed. */
	@Column(name = "read_status", nullable = false)
	private int readStatus;

	protected NotificationReceipt() {
	}

	NotificationReceipt(long notificationId, UUID userId) {
		this.notificationId = notificationId;
		this.userId = userId;
	}

	void markDelivered() {
		delivered = true;
	}

	void markStatus(int status) {
		if (status < 0 || status > 2) {
			throw new IllegalArgumentException("Unknown notification status " + status);
		}
		readStatus = Math.max(readStatus, status);
	}

	long getNotificationId() { return notificationId; }
	boolean isDelivered() { return delivered; }
	int getReadStatus() { return readStatus; }
}
