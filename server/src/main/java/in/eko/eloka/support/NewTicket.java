package in.eko.eloka.support;

import java.util.List;
import java.util.UUID;

/**
 * A ticket raised by a user.
 *
 * @param description plain text (HTML is stripped before storing)
 * @param comment the user's own words; becomes the first message on the ticket
 * @param attachmentIds documents already stored through the documents module
 */
public record NewTicket(String summary, String description, String comment, String category, String subCategory,
		String issueType, Long tid, Integer txTypeId, String priority, Integer tatHours, String origin,
		List<UUID> attachmentIds) {
}
