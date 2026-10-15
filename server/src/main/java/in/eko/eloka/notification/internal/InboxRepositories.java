package in.eko.eloka.notification.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {

	@Query("select n from InAppNotification n where n.orgId = :orgId and (n.userId = :userId or n.userId is null) order by n.notifyTime desc")
	List<InAppNotification> inbox(@Param("orgId") long orgId, @Param("userId") UUID userId, Pageable page);

	@Query("select n from InAppNotification n where n.id = :id and n.orgId = :orgId and (n.userId = :userId or n.userId is null)")
	Optional<InAppNotification> findVisible(@Param("id") long id, @Param("orgId") long orgId, @Param("userId") UUID userId);
}

interface NotificationReceiptRepository extends JpaRepository<NotificationReceipt, UUID> {

	List<NotificationReceipt> findByUserIdAndNotificationIdIn(UUID userId, Collection<Long> ids);

	Optional<NotificationReceipt> findByNotificationIdAndUserId(long notificationId, UUID userId);
}
