package in.eko.eloka.notification.internal;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/** A published notification (for one user, or the whole org when {@code userId} is null). */
@Entity
@Table(name = "notification")
class InAppNotification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "org_id", nullable = false)
	private long orgId;

	@Column(name = "user_id", columnDefinition = "BINARY(16)")
	private UUID userId;

	@Column(nullable = false, length = 10)
	private String type;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(length = 1000)
	private String body;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Column(length = 500)
	private String link;

	@Column(name = "link_label", length = 60)
	private String linkLabel;

	@Column(nullable = false)
	private int priority;

	@Column(name = "notify_time", nullable = false)
	private Instant notifyTime;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected InAppNotification() {
	}

	InAppNotification(long orgId, UUID userId, String type, String title, String body, String imageUrl, String link,
			String linkLabel, int priority, Instant notifyTime) {
		this.orgId = orgId;
		this.userId = userId;
		this.type = type;
		this.title = title;
		this.body = body;
		this.imageUrl = imageUrl;
		this.link = link;
		this.linkLabel = linkLabel;
		this.priority = priority;
		this.notifyTime = notifyTime;
	}

	Long getId() { return id; }
	String getType() { return type; }
	String getTitle() { return title; }
	String getBody() { return body; }
	String getImageUrl() { return imageUrl; }
	String getLink() { return link; }
	String getLinkLabel() { return linkLabel; }
	int getPriority() { return priority; }
	Instant getNotifyTime() { return notifyTime; }
}
