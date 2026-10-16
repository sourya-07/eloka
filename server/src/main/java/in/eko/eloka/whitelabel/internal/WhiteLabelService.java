package in.eko.eloka.whitelabel.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import in.eko.eloka.shared.error.ApiProblem;
import in.eko.eloka.tenant.OrgApi;
import in.eko.eloka.whitelabel.BrandingDraftInput;
import in.eko.eloka.whitelabel.SubdomainAvailability;
import in.eko.eloka.whitelabel.SubdomainReservation;
import in.eko.eloka.whitelabel.WhiteLabelApi;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class WhiteLabelService implements WhiteLabelApi {

	/** DNS label rules plus a sensible length for a brand (3–40). */
	private static final Pattern LABEL = Pattern.compile("^[a-z0-9]([a-z0-9-]{1,38}[a-z0-9])$");

	private final DomainReservationRepository reservations;
	private final BrandingDraftRepository brandings;
	private final OrgApi orgs;
	private final EntityManager entityManager;
	private final JsonMapper json;
	private final Clock clock;

	WhiteLabelService(DomainReservationRepository reservations, BrandingDraftRepository brandings, OrgApi orgs,
			EntityManager entityManager, JsonMapper json, Clock clock) {
		this.reservations = reservations;
		this.brandings = brandings;
		this.orgs = orgs;
		this.entityManager = entityManager;
		this.json = json;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public SubdomainAvailability availability(String rawLabel) {
		String label = normalize(rawLabel);
		String reason = unavailableReason(label, null);
		return new SubdomainAvailability(label, reason == null, reason, reason == null ? List.of() : suggestions(label),
				"https://" + orgs.hostnameForSubdomain(label));
	}

	@Override
	@Transactional
	public SubdomainReservation reserve(UUID applicationId, String rawLabel) {
		String label = normalize(rawLabel);
		Instant now = Instant.now(clock);
		List<DomainReservation> held = reservations.findByApplicationIdAndStatus(applicationId, DomainReservation.RESERVED);
		Optional<DomainReservation> same = held.stream().filter(r -> r.getLabel().equals(label)).findFirst();
		if (same.isPresent()) {
			return view(same.get());
		}
		String reason = unavailableReason(label, applicationId);
		if (reason != null) {
			throw taken(label, reason);
		}
		held.forEach(r -> r.release(now));
		reservations.flush();
		try {
			return view(reservations.saveAndFlush(new DomainReservation(applicationId, label,
					orgs.hostnameForSubdomain(label), now)));
		}
		catch (DataIntegrityViolationException race) {
			// Another application reserved it between our check and our insert.
			throw taken(label, "TAKEN");
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<SubdomainReservation> reservationOf(UUID applicationId) {
		return reservations.findByApplicationIdAndStatus(applicationId, DomainReservation.RESERVED)
			.stream()
			.findFirst()
			.map(WhiteLabelService::view);
	}

	@Override
	@Transactional
	public void saveBranding(UUID applicationId, BrandingDraftInput draft) {
		BrandingDraft branding = brandings.findByApplicationId(applicationId)
			.orElseGet(() -> new BrandingDraft(applicationId));
		branding.update(draft.appName(), draft.logoDocumentId(),
				draft.theme().isEmpty() ? null : json.writeValueAsString(draft.theme()));
		brandings.save(branding);
	}

	@Override
	@Transactional
	public void release(UUID applicationId) {
		Instant now = Instant.now(clock);
		reservations.findByApplicationIdAndStatus(applicationId, DomainReservation.RESERVED).forEach(r -> r.release(now));
	}

	private String unavailableReason(String label, UUID applicationId) {
		if (!LABEL.matcher(label).matches()) {
			return "INVALID_FORMAT";
		}
		if (isReservedWord(label)) {
			return "RESERVED_WORD";
		}
		String hostname = orgs.hostnameForSubdomain(label);
		if (orgs.findByHostname(hostname).isPresent()) {
			return "TAKEN";
		}
		boolean heldByOther = reservations.isHeld(hostname) && (applicationId == null
				|| reservations.findByApplicationIdAndStatus(applicationId, DomainReservation.RESERVED)
					.stream()
					.noneMatch(r -> r.getHostname().equals(hostname)));
		return heldByOther ? "TAKEN" : null;
	}

	private boolean isReservedWord(String label) {
		Number count = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM reserved_label WHERE label = ?1")
			.setParameter(1, label)
			.getSingleResult();
		return count.longValue() > 0;
	}

	private List<String> suggestions(String label) {
		String base = label.replaceAll("[^a-z0-9-]", "").replaceAll("^-+|-+$", "");
		if (base.length() < 3) {
			base = base + "store";
		}
		List<String> out = new ArrayList<>();
		for (String candidate : List.of(base + "-pay", base + "-online", base + "-india", base + "pay", base + "24")) {
			if (out.size() < 3 && unavailableReason(candidate, null) == null) {
				out.add(candidate);
			}
		}
		return out;
	}

	private ApiProblem taken(String label, String reason) {
		String message = switch (reason) {
			case "INVALID_FORMAT" -> "Use 3–40 lowercase letters, digits or hyphens (not at the start or end)";
			case "RESERVED_WORD" -> "This name is reserved. Please choose another.";
			default -> "This sub-domain is already taken";
		};
		return new ApiProblem("INVALID_FORMAT".equals(reason) ? HttpStatus.UNPROCESSABLE_CONTENT : HttpStatus.CONFLICT,
				"INVALID_FORMAT".equals(reason) ? "VALIDATION_FAILED" : "SUBDOMAIN_TAKEN", message,
				java.util.Map.of("reason", reason, "suggestions", "INVALID_FORMAT".equals(reason) ? List.of() : suggestions(label)));
	}

	private static String normalize(String label) {
		return label == null ? "" : label.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private static SubdomainReservation view(DomainReservation reservation) {
		return new SubdomainReservation(reservation.getLabel(), reservation.getHostname());
	}
}
