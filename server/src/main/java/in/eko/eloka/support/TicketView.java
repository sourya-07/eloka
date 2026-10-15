package in.eko.eloka.support;

import java.time.Instant;

/** A ticket as listed in the Query Center. */
public record TicketView(long id, String ticketNumber, String summary, String category, String subCategory,
		TicketStatus status, String priority, Instant createdAt, Instant updatedAt, java.util.UUID raisedBy,
		String raisedByCode) {
}
