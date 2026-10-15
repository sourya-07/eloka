package in.eko.eloka.support;

import java.util.List;
import in.eko.eloka.shared.security.Caller;

/** Public API of the support module. Every call is scoped to what the caller may see. */
public interface SupportApi {

	TicketView raise(Caller caller, NewTicket ticket);

	/** Org admin: all tickets of the org. Others: their own. Newest first. */
	List<TicketView> visibleTo(Caller caller);

	List<CommentView> comments(Caller caller, long ticketId);

	void comment(Caller caller, long ticketId, String text);

	void escalate(Caller caller, long ticketId);

	void close(Caller caller, long ticketId);
}
