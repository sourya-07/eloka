package in.eko.eloka.notification;

import java.util.List;
import java.util.UUID;

/** In-app notification inbox. */
public interface NotificationApi {

	long publish(NewNotification notification);

	/** Newest first: the user's own notifications plus org-wide ones. Dismissed ones are left out. */
	List<InboxItem> inbox(long orgId, UUID userId, int limit);

	void markDelivered(long orgId, UUID userId, long notificationId);

	/** @param status 1 read, 2 dismissed */
	void markStatus(long orgId, UUID userId, long notificationId, int status);
}
