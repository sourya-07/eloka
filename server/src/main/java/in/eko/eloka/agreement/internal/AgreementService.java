package in.eko.eloka.agreement.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.agreement.AgreementApi;
import in.eko.eloka.agreement.AgreementContent;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.documents.DocumentApi;
import in.eko.eloka.documents.DocumentPurpose;
import in.eko.eloka.documents.NewDocument;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.integration.EsignProvider;
import in.eko.eloka.shared.error.ConflictException;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.persistence.PublicIds;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agreement versions and e-sign. Security rule (state machine doc §5.3): {@code SIGNED} is set
 * only here, after the provider confirms; nothing the browser sends can mark an agreement signed.
 */
@Service
class AgreementService implements AgreementApi {

	private static final Duration ESIGN_SESSION_TTL = Duration.ofMinutes(30);

	private final AgreementRepository agreements;
	private final EsignRequestRepository requests;
	private final AgreementRenderer renderer;
	private final DocumentApi documents;
	private final EsignProvider esign;
	private final AuditLog audit;
	private final Clock clock;

	AgreementService(AgreementRepository agreements, EsignRequestRepository requests, AgreementRenderer renderer,
			DocumentApi documents, EsignProvider esign, AuditLog audit, Clock clock) {
		this.agreements = agreements;
		this.requests = requests;
		this.renderer = renderer;
		this.documents = documents;
		this.esign = esign;
		this.audit = audit;
		this.clock = clock;
	}

	@Override
	@Transactional
	public AgreementView prepare(UUID applicationId, UUID ownerId, AgreementContent content) {
		String text = renderer.text(content);
		byte[] sha = sha256(text);
		List<Agreement> versions = agreements.findByApplicationIdOrderByVersionNoDesc(applicationId);
		Optional<Agreement> live = versions.stream().filter(Agreement::isLive).findFirst();
		if (live.isPresent() && MessageDigest.isEqual(live.get().getContentSha256(), sha)) {
			return view(live.get());
		}
		live.ifPresent(Agreement::supersede);
		Instant now = Instant.now(clock);
		byte[] pdf = renderer.pdf(text, LocalDate.ofInstant(now, java.time.ZoneId.of("Asia/Kolkata")));
		var stored = documents.store(new NewDocument(IdentityApi.PLATFORM_ORG_ID, ownerId, ownerId,
				DocumentPurpose.LOCAL_ADMIN_APPLICATION, "agreement.pdf", pdf));
		int nextVersion = versions.isEmpty() ? 1 : versions.get(0).getVersionNo() + 1;
		Agreement agreement = agreements.save(new Agreement(applicationId, PublicIds.next("agr", now), nextVersion,
				AgreementRenderer.TEMPLATE_VERSION, sha, stored.id()));
		audit.record(new AuditEntry("AGREEMENT_GENERATED", "APPLICANT", null, IdentityApi.PLATFORM_ORG_ID, "AGREEMENT",
				agreement.getPublicId(), Map.of("version", Integer.toString(nextVersion))));
		return view(agreement);
	}

	@Override
	@Transactional
	public EsignStart startEsign(UUID applicationId, String agreementId, UUID signerId) {
		Agreement agreement = find(applicationId, agreementId);
		if (agreement.getStatus().equals(Agreement.SIGNED)) {
			throw new ConflictException("ALREADY_SIGNED", "This agreement is already signed");
		}
		if (!agreement.isLive()) {
			throw new ConflictException("AGREEMENT_SUPERSEDED", "This agreement was replaced by a newer version");
		}
		Instant now = Instant.now(clock);
		Optional<EsignRequest> open = requests.findByAgreementIdOrderByCreatedAtDesc(agreement.getId())
			.stream()
			.filter(request -> request.isOpen(now))
			.findFirst();
		if (open.isPresent()) {
			return start(open.get(), false);
		}
		EsignProvider.EsignSession session = esign.prepare(signerId, agreement.getPublicId());
		EsignRequest request = requests.save(new EsignRequest(agreement.getId(), PublicIds.next("esr", now), session.pipe(),
				session.documentId(), session.signingUrl(), now.plus(ESIGN_SESSION_TTL)));
		agreement.signing();
		return start(request, session.alreadySigned());
	}

	@Override
	@Transactional
	public AgreementView status(UUID applicationId, String agreementId) {
		Agreement agreement = find(applicationId, agreementId);
		if (agreement.getStatus().equals(Agreement.SIGNING)) {
			Instant now = Instant.now(clock);
			for (EsignRequest request : requests.findByAgreementIdOrderByCreatedAtDesc(agreement.getId())) {
				if (esign.isSigned(request.getProviderDocumentId())) {
					request.close("SIGNED");
					agreement.signed(now, null);
					audit.record(new AuditEntry("AGREEMENT_SIGNED", "APPLICANT", null, IdentityApi.PLATFORM_ORG_ID,
							"AGREEMENT", agreement.getPublicId(), Map.of("version", Integer.toString(agreement.getVersionNo()))));
					break;
				}
			}
		}
		return view(agreement);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<AgreementView> current(UUID applicationId) {
		return agreements.findByApplicationIdOrderByVersionNoDesc(applicationId)
			.stream()
			.filter(Agreement::isLive)
			.findFirst()
			.map(AgreementService::view);
	}

	@Override
	@Transactional(readOnly = true)
	public byte[] pdf(UUID applicationId, String agreementId) {
		return documents.load(IdentityApi.PLATFORM_ORG_ID, find(applicationId, agreementId).getDocumentId()).bytes();
	}

	private Agreement find(UUID applicationId, String agreementId) {
		return agreements.findByPublicIdAndApplicationId(agreementId, applicationId)
			.orElseThrow(() -> new NotFoundException("AGREEMENT_NOT_FOUND", "Agreement not found"));
	}

	private static EsignStart start(EsignRequest request, boolean alreadySigned) {
		return new EsignStart(request.getPublicId(), request.getProviderPipe(), request.getSigningUrl(),
				request.getProviderDocumentId(), request.getExpiresAt(), alreadySigned);
	}

	private static AgreementView view(Agreement agreement) {
		return new AgreementView(agreement.getPublicId(), agreement.getVersionNo(), agreement.getTemplateVersion(),
				agreement.getStatus(), HexFormat.of().formatHex(agreement.getContentSha256()), agreement.getSignedAt(),
				agreement.getSignerName());
	}

	private static byte[] sha256(String text) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
