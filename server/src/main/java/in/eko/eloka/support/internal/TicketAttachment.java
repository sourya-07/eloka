package in.eko.eloka.support.internal;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Links a stored document to a ticket. */
@Entity
@Table(name = "support_ticket_attachment")
class TicketAttachment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ticket_id", nullable = false)
	private long ticketId;

	@Column(name = "document_id", nullable = false, columnDefinition = "BINARY(16)")
	private UUID documentId;

	protected TicketAttachment() {
	}

	TicketAttachment(long ticketId, UUID documentId) {
		this.ticketId = ticketId;
		this.documentId = documentId;
	}
}
