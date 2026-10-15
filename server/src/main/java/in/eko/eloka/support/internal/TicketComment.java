package in.eko.eloka.support.internal;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/** One comment on a ticket. Immutable. */
@Entity
@Table(name = "support_ticket_comment")
class TicketComment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ticket_id", nullable = false)
	private long ticketId;

	@Column(name = "author_id", columnDefinition = "BINARY(16)")
	private UUID authorId;

	/** ADMIN, AGENT or SUPPORT. */
	@Column(name = "author_role", nullable = false, length = 10)
	private String authorRole;

	@Column(nullable = false, length = 2000)
	private String body;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected TicketComment() {
	}

	TicketComment(long ticketId, UUID authorId, String authorRole, String body) {
		this.ticketId = ticketId;
		this.authorId = authorId;
		this.authorRole = authorRole;
		this.body = body;
	}

	UUID getAuthorId() { return authorId; }
	String getAuthorRole() { return authorRole; }
	String getBody() { return body; }
	Instant getCreatedAt() { return createdAt; }
}
