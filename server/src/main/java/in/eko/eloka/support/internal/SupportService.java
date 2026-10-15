package in.eko.eloka.support.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.Role;
import in.eko.eloka.support.CommentView;
import in.eko.eloka.support.NewTicket;
import in.eko.eloka.support.SupportApi;
import in.eko.eloka.support.TicketView;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

	List<SupportTicket> findByOrgId(long orgId, Sort sort);

	List<SupportTicket> findByOrgIdAndUserId(long orgId, UUID userId, Sort sort);
}

interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {

	List<TicketComment> findByTicketIdOrderByCreatedAt(long ticketId);
}

interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {
}

@Service
@Transactional(readOnly = true)
class SupportService implements SupportApi {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

	private final SupportTicketRepository tickets;
	private final TicketCommentRepository comments;
	private final TicketAttachmentRepository attachments;
	private final AuditLog audit;

	SupportService(SupportTicketRepository tickets, TicketCommentRepository comments,
			TicketAttachmentRepository attachments, AuditLog audit) {
		this.tickets = tickets;
		this.comments = comments;
		this.attachments = attachments;
		this.audit = audit;
	}

	@Override
	@Transactional
	public TicketView raise(Caller caller, NewTicket t) {
		if (t.summary() == null || t.summary().isBlank()) {
			throw ValidationException.field("summary", "Please describe the issue");
		}
		SupportTicket ticket = tickets.save(new SupportTicket(caller.orgId(), caller.userId(), caller.userCode(), null,
				trim(t.summary(), 200), trim(stripHtml(t.description()), 4000), t.category(), t.subCategory(), t.issueType(),
				t.tid(), t.txTypeId(), t.priority(), t.tatHours(), t.origin()));
		if (t.comment() != null && !t.comment().isBlank()) {
			comments.save(new TicketComment(ticket.getId(), caller.userId(), "AGENT", trim(stripHtml(t.comment()), 2000)));
		}
		if (t.attachmentIds() != null) {
			t.attachmentIds().forEach(doc -> attachments.save(new TicketAttachment(ticket.getId(), doc)));
		}
		audit.record(new AuditEntry("TICKET_RAISED", "USER", caller.userCode(), caller.orgId(), "TICKET",
				ticket.getId().toString(), Map.of("category", String.valueOf(t.category()))));
		return toView(ticket);
	}

	@Override
	public List<TicketView> visibleTo(Caller caller) {
		List<SupportTicket> list = caller.role() == Role.ORG_ADMIN ? tickets.findByOrgId(caller.orgId(), NEWEST_FIRST)
				: tickets.findByOrgIdAndUserId(caller.orgId(), caller.userId(), NEWEST_FIRST);
		return list.stream().map(SupportService::toView).toList();
	}

	@Override
	public List<CommentView> comments(Caller caller, long ticketId) {
		visible(caller, ticketId);
		return comments.findByTicketIdOrderByCreatedAt(ticketId).stream()
			.map(c -> new CommentView(caller.userId().equals(c.getAuthorId()) ? "ME" : c.getAuthorRole(), c.getBody(),
					c.getCreatedAt()))
			.toList();
	}

	@Override
	@Transactional
	public void comment(Caller caller, long ticketId, String text) {
		if (text == null || text.isBlank()) {
			throw ValidationException.field("comment", "Comment cannot be empty");
		}
		SupportTicket ticket = visible(caller, ticketId);
		ticket.requireOpen();
		comments.save(new TicketComment(ticketId, caller.userId(), caller.role() == Role.ORG_ADMIN ? "ADMIN" : "AGENT",
				trim(stripHtml(text), 2000)));
	}

	@Override
	@Transactional
	public void escalate(Caller caller, long ticketId) {
		visible(caller, ticketId).escalate();
		audit.record(new AuditEntry("TICKET_ESCALATED", "USER", caller.userCode(), caller.orgId(), "TICKET",
				Long.toString(ticketId), Map.of()));
	}

	@Override
	@Transactional
	public void close(Caller caller, long ticketId) {
		visible(caller, ticketId).close();
		audit.record(new AuditEntry("TICKET_CLOSED", "USER", caller.userCode(), caller.orgId(), "TICKET",
				Long.toString(ticketId), Map.of()));
	}

	private SupportTicket visible(Caller caller, long ticketId) {
		return tickets.findById(ticketId)
			.filter(t -> t.getOrgId() == caller.orgId())
			.filter(t -> caller.role() == Role.ORG_ADMIN || t.getUserId().equals(caller.userId()))
			.orElseThrow(() -> new NotFoundException("TICKET_NOT_FOUND", "Ticket not found"));
	}

	private static TicketView toView(SupportTicket t) {
		return new TicketView(t.getId(), t.ticketNumber(), t.getSummary(), t.getCategory(), t.getSubCategory(), t.getStatus(),
				t.getPriority(), t.getCreatedAt(), t.getUpdatedAt(), t.getUserId(), t.getUserCode());
	}

	/** Tickets are shown as text; drop any markup the client sends. */
	static String stripHtml(String value) {
		if (value == null) {
			return null;
		}
		return value.replaceAll("(?i)<br\\s*/?>|</p>|</div>|</li>", "\n").replaceAll("<[^>]*>", "")
			.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
			.replaceAll("\n{3,}", "\n\n").trim();
	}

	private static String trim(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}
}
