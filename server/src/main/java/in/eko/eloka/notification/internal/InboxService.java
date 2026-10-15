package in.eko.eloka.notification.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import in.eko.eloka.notification.InboxItem;
import in.eko.eloka.notification.NewNotification;
import in.eko.eloka.notification.NotificationApi;
import in.eko.eloka.shared.error.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class InboxService implements NotificationApi {

	private final InAppNotificationRepository notifications;
	private final NotificationReceiptRepository receipts;

	InboxService(InAppNotificationRepository notifications, NotificationReceiptRepository receipts) {
		this.notifications = notifications;
		this.receipts = receipts;
	}

	@Override
	@Transactional
	public long publish(NewNotification n) {
		return notifications.save(new InAppNotification(n.orgId(), n.userId(), n.type(), n.title(), n.body(), n.imageUrl(),
				n.link(), n.linkLabel(), n.priority(), n.notifyTime())).getId();
	}

	@Override
	public List<InboxItem> inbox(long orgId, UUID userId, int limit) {
		List<InAppNotification> items = notifications.inbox(orgId, userId, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
		Map<Long, NotificationReceipt> state = receipts
			.findByUserIdAndNotificationIdIn(userId, items.stream().map(InAppNotification::getId).toList())
			.stream()
			.collect(Collectors.toMap(NotificationReceipt::getNotificationId, Function.identity()));
		return items.stream()
			.map(n -> {
				NotificationReceipt r = state.get(n.getId());
				return new InboxItem(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getImageUrl(), n.getLink(),
						n.getLinkLabel(), n.getPriority(), n.getNotifyTime(), r == null ? 0 : r.getReadStatus(),
						r != null && r.isDelivered());
			})
			.filter(item -> item.read() != 2)
			.toList();
	}

	@Override
	@Transactional
	public void markDelivered(long orgId, UUID userId, long notificationId) {
		receipt(orgId, userId, notificationId).markDelivered();
	}

	@Override
	@Transactional
	public void markStatus(long orgId, UUID userId, long notificationId, int status) {
		receipt(orgId, userId, notificationId).markStatus(status);
	}

	private NotificationReceipt receipt(long orgId, UUID userId, long notificationId) {
		notifications.findVisible(notificationId, orgId, userId)
			.orElseThrow(() -> new NotFoundException("NOTIFICATION_NOT_FOUND", "Notification not found"));
		return receipts.findByNotificationIdAndUserId(notificationId, userId)
			.orElseGet(() -> receipts.save(new NotificationReceipt(notificationId, userId)));
	}
}
