package in.eko.eloka.support.internal;

import java.time.Instant;
import java.util.UUID;
import in.eko.eloka.shared.error.ConflictException;
import in.eko.eloka.support.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A support ticket. Owns its lifecycle: escalate and close only from allowed states. */
@Entity
@Table(name = "support_ticket")
class SupportTicket {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "org_id", nullable = false)
	private long orgId;

	@Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID userId;

	@Column(name = "user_code", nullable = false, length = 20)
	private String userCode;

	@Column(name = "user_name", length = 150)
	private String userName;

	@Column(nullable = false, length = 200)
	private String summary;

	@Column(length = 4000)
	private String description;

	@Column(length = 80)
	private String category;

	@Column(name = "sub_category", length = 80)
	private String subCategory;

	@Column(name = "issue_type", length = 40)
	private String issueType;

	private Long tid;

	@Column(name = "tx_typeid")
	private Integer txTypeId;

	@Column(length = 20)
	private String priority;

	@Column(name = "tat_hours")
	private Integer tatHours;

	@Column(length = 40)
	private String origin;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TicketStatus status;

	@Version
	private long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected SupportTicket() {
	}

	SupportTicket(long orgId, UUID userId, String userCode, String userName, String summary, String description,
			String category, String subCategory, String issueType, Long tid, Integer txTypeId, String priority,
			Integer tatHours, String origin) {
		this.orgId = orgId;
		this.userId = userId;
		this.userCode = userCode;
		this.userName = userName;
		this.summary = summary;
		this.description = description;
		this.category = category;
		this.subCategory = subCategory;
		this.issueType = issueType;
		this.tid = tid;
		this.txTypeId = txTypeId;
		this.priority = priority;
		this.tatHours = tatHours;
		this.origin = origin;
		this.status = TicketStatus.OPEN;
	}

	void escalate() {
		requireOpen();
		status = TicketStatus.ESCALATED;
	}

	void close() {
		requireOpen();
		status = TicketStatus.CLOSED;
	}

	void requireOpen() {
		if (status == TicketStatus.CLOSED) {
			throw new ConflictException("TICKET_CLOSED", "This ticket is already closed");
		}
	}

	/** Human-facing number, e.g. {@code ELK-000123}. */
	String ticketNumber() {
		return String.format("ELK-%06d", id);
	}

	Long getId() { return id; }
	long getOrgId() { return orgId; }
	UUID getUserId() { return userId; }
	String getUserCode() { return userCode; }
	String getUserName() { return userName; }
	String getSummary() { return summary; }
	String getCategory() { return category; }
	String getSubCategory() { return subCategory; }
	TicketStatus getStatus() { return status; }
	String getPriority() { return priority; }
	Instant getCreatedAt() { return createdAt; }
	Instant getUpdatedAt() { return updatedAt; }
}
