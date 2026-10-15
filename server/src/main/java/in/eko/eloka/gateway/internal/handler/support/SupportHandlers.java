package in.eko.eloka.gateway.internal.handler.support;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.documents.DocumentApi;
import in.eko.eloka.documents.DocumentPurpose;
import in.eko.eloka.documents.NewDocument;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsParams;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.gateway.internal.envelope.UploadedFile;
import in.eko.eloka.gateway.internal.handler.EpsDates;
import in.eko.eloka.network.NetworkQueries;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.support.IssueType;
import in.eko.eloka.support.IssueTypes;
import in.eko.eloka.support.NewTicket;
import in.eko.eloka.support.SupportApi;
import in.eko.eloka.support.TicketStatus;
import in.eko.eloka.support.TicketView;
import org.springframework.stereotype.Component;

/** Raise issue (10022 types, 10000 create) and the Query Center (692, 10016, 10017, 10018, 10020). */
final class SupportHandlers {

	private static final DateTimeFormatter UTC_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
		.withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter LOCAL_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
		.withZone(EpsDates.IST);

	private SupportHandlers() {
	}

	/** 10022: issue types for the context ({@code tx_typeid}: -1 general, -2 onboarding, else a transaction type). */
	@Component
	static class Types implements EpsHandler {

		private final IssueTypes types;

		Types(IssueTypes types) {
			this.types = types;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(10022));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.SESSION;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			int context = call.params().integer("tx_typeid", -1);
			List<Map<String, Object>> list = types.forContext(context).stream().map(Types::item).toList();
			return EpsEnvelope.ok().data(Map.of("issuetype_list", list)).build();
		}

		private static Map<String, Object> item(IssueType t) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("label", t.label());
			item.put("value", t.label());
			item.put("type", 0);
			item.put("desc", t.description() == null ? t.label() : t.description());
			item.put("tat", t.tatDays());
			item.put("priority", t.priority());
			item.put("comment", t.comment());
			item.put("raise_issue_after", "0d");
			item.put("category", Map.of("id", t.categoryId(), "title", t.category()));
			item.put("sub_category", Map.of("id", t.subCategoryId(), "title", t.subCategory()));
			item.put("inputs", t.inputs().stream().map(i -> Map.of("label", i.label(), "type", i.type(), "is_required",
					i.required() ? 1 : 0, "length_max", i.maxLength())).toList());
			item.put("files", t.files().stream().map(f -> Map.of("label", f.label(), "is_required", f.required() ? 1 : 0))
				.toList());
			return item;
		}
	}

	/** 10000: create a ticket (JSON, or multipart via /transactions/upload with attachments). */
	@Component
	static class Raise implements EpsHandler {

		private final SupportApi support;
		private final DocumentApi documents;

		Raise(SupportApi support, DocumentApi documents) {
			this.support = support;
			this.documents = documents;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(10000));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			EpsParams p = call.params();
			List<UUID> attachments = new ArrayList<>();
			for (UploadedFile file : call.files()) {
				attachments.add(documents.store(new NewDocument(caller.orgId(), caller.userId(), caller.userId(),
						DocumentPurpose.TICKET_ATTACHMENT, file.filename(), file.bytes())).id());
			}
			String summary = p.string("summary").or(() -> p.string("feedback_issue_type")).orElse("Support request");
			TicketView ticket = support.raise(caller, new NewTicket(summary, p.string("description").orElse(null),
					p.string("comment").orElse(null), p.string("category").orElse(null), p.string("sub_category").orElse(null),
					p.string("feedback_issue_type").orElse(null), p.longValue("tid").orElse(null),
					p.integer("tx_typeid").orElse(null), p.string("priority").orElse(null), p.integer("tat").orElse(null),
					p.string("feedback_origin").orElse(null), attachments));
			return EpsEnvelope.ok().message("Your query has been registered")
				.data(Map.of("feedback_ticket_id", ticket.ticketNumber())).build();
		}
	}

	/** 692: tickets the caller may see (org admin: whole org). */
	@Component
	static class ListTickets implements EpsHandler {

		private final SupportApi support;
		private final NetworkQueries network;

		ListTickets(SupportApi support, NetworkQueries network) {
			this.support = support;
			this.network = network;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(692));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			List<TicketView> tickets = support.visibleTo(caller);
			Map<UUID, UserView> raisers = network.byIds(caller.orgId(), tickets.stream().map(TicketView::raisedBy).distinct().toList());
			List<Map<String, Object>> rows = tickets.stream().map(t -> {
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("id", Long.toString(t.id()));
				row.put("ticketNumber", t.ticketNumber());
				row.put("subject", t.summary());
				row.put("subCategory", t.subCategory() == null ? "Others" : t.subCategory());
				row.put("status", switch (t.status()) {
					case OPEN -> "Open";
					case ESCALATED -> "Escalated";
					case CLOSED -> "Closed";
				});
				row.put("statusType", t.status() == TicketStatus.CLOSED ? "Closed" : "Open");
				row.put("priority", t.priority());
				UserView raiser = raisers.get(t.raisedBy());
				row.put("contactName", raiser == null ? t.raisedByCode() : raiser.name());
				row.put("CSP Code", t.raisedByCode());
				row.put("eko_code", t.raisedByCode());
				row.put("createdTime", UTC_ISO.format(t.createdAt()));
				row.put("modifiedTime", UTC_ISO.format(t.updatedAt()));
				return row;
			}).toList();
			return EpsEnvelope.ok().data(Map.of("csp_list", rows)).build();
		}
	}

	/** 10016 comments, 10017 add comment, 10018 escalate, 10020 close. */
	@Component
	static class TicketActions implements EpsHandler {

		private final SupportApi support;

		TicketActions(SupportApi support) {
			this.support = support;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(10016), RouteKey.interaction(10017), RouteKey.interaction(10018),
					RouteKey.interaction(10020));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			long ticketId = call.params().longValue("feedback_ticket_id")
				.orElseThrow(() -> in.eko.eloka.shared.error.ValidationException.field("feedback_ticket_id", "Ticket is required"));
			return switch (call.request().interactionId().orElse("")) {
				case "10016" -> {
					List<Map<String, Object>> list = support.comments(caller, ticketId).stream().map(c -> {
						String stamp = LOCAL_ISO.format(c.at());
						return Map.<String, Object>of("comment", marker(c.authorRole()) + " " + c.text(), "timestamp", stamp,
								"date_updated", stamp);
					}).toList();
					yield EpsEnvelope.ok().data(Map.of("comment_list", list)).build();
				}
				case "10017" -> {
					support.comment(caller, ticketId, call.params().requireString("comment"));
					yield EpsEnvelope.ok().message("Comment added").build();
				}
				case "10018" -> {
					support.escalate(caller, ticketId);
					yield EpsEnvelope.ok().message("Ticket escalated").build();
				}
				default -> {
					support.close(caller, ticketId);
					yield EpsEnvelope.ok().message("Ticket closed").build();
				}
			};
		}

		/** The client parses these prefixes to decide who wrote a comment. */
		private static String marker(String role) {
			return switch (role) {
				case "ME" -> "**Me:**";
				case "ADMIN" -> "**Admin:**";
				case "AGENT" -> "**Agent:**";
				default -> "**Eko:**";
			};
		}
	}
}
