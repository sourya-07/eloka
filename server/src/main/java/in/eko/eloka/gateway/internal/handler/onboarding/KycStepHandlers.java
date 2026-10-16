package in.eko.eloka.gateway.internal.handler.onboarding;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.agentonboarding.AgentOnboardingApi;
import in.eko.eloka.agentonboarding.BankAccountDetails;
import in.eko.eloka.agentonboarding.BusinessDetails;
import in.eko.eloka.agentonboarding.OnboardingSubject;
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
import in.eko.eloka.integration.DigiLockerProvider;
import in.eko.eloka.integration.EsignProvider;
import in.eko.eloka.network.NetworkCommands;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.shared.error.ConflictException;
import in.eko.eloka.shared.error.ValidationException;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** KYC steps of agent onboarding. Success ids are the ones features/onboarding waits for. */
final class KycStepHandlers {

	private KycStepHandlers() {
	}

	/** 523: document upload (multipart). doc_type 1 Aadhaar (2 sides), 2 PAN (+ doc_id), 3 selfie, 7 passbook. */
	@Component
	static class Upload implements EpsHandler {

		static final int UPLOADED = 1569;

		private final OnboardingSubjects subjects;
		private final AgentOnboardingApi onboarding;
		private final DocumentApi documents;

		Upload(OnboardingSubjects subjects, AgentOnboardingApi onboarding, DocumentApi documents) {
			this.subjects = subjects;
			this.onboarding = onboarding;
			this.documents = documents;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(523));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			OnboardingSubject s = subjects.resolve(call);
			int docType = call.params().integer("doc_type").orElseThrow(() -> ValidationException.field("doc_type", "doc_type is required"));
			switch (docType) {
				case 1 -> onboarding.saveAadhaarImages(s, store(call, s, "file1", DocumentPurpose.AADHAAR_FRONT),
						store(call, s, "file2", DocumentPurpose.AADHAAR_BACK));
				case 2 -> onboarding.savePan(s, pan(call.params()), store(call, s, "file1", DocumentPurpose.PAN_CARD));
				case 3 -> onboarding.saveSelfie(s, store(call, s, "file1", DocumentPurpose.SELFIE));
				case 7 -> onboarding.saveBankProof(s, store(call, s, "file1", DocumentPurpose.BANK_PROOF));
				default -> throw ValidationException.field("doc_type", "Unsupported document type");
			}
			return EpsEnvelope.ok().type(UPLOADED).message("Document uploaded").build();
		}

		private UUID store(EpsCall call, OnboardingSubject s, String field, DocumentPurpose purpose) {
			UploadedFile file = call.files().stream().filter(f -> field.equals(f.field())).findFirst()
				.orElseThrow(() -> ValidationException.field(field, "Please attach the file"));
			return documents.store(new NewDocument(s.orgId(), s.userId(), s.actorUserId(), purpose, file.filename(), file.bytes()))
				.id();
		}

		private static String pan(EpsParams params) {
			String pan = params.string("doc_id").orElse("").trim().toUpperCase();
			if (!pan.matches("[A-Z]{5}[0-9]{4}[A-Z]")) {
				throw ValidationException.field("doc_id", "Enter a valid PAN (for example ABCDE1234F)");
			}
			return pan;
		}
	}

	/** 298: selfie step, part 1: shop location and type. Success 1076. */
	@Component
	static class Location implements EpsHandler {

		private final OnboardingSubjects subjects;
		private final AgentOnboardingApi onboarding;
		private final NetworkCommands network;
		private final UserDirectory users;

		Location(OnboardingSubjects subjects, AgentOnboardingApi onboarding, NetworkCommands network, UserDirectory users) {
			this.subjects = subjects;
			this.onboarding = onboarding;
			this.network = network;
			this.users = users;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(298));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			OnboardingSubject s = subjects.resolve(call);
			String latlong = call.params().string("latlong").orElse("");
			BigDecimal[] point = parseLatLong(latlong);
			onboarding.saveLocation(s, latlong, call.params().string("shop_type").orElse(null));
			String userCode = users.getById(s.orgId(), s.userId()).userCode();
			network.updateShop(call.caller(), userCode, new ShopView(call.params().string("shop_name").orElse(null),
					call.params().string("shop_type").orElse(null), null, null, null, null, null, null, null, point[0], point[1]));
			return EpsEnvelope.ok().type(1076).message("Location saved").build();
		}

		private static BigDecimal[] parseLatLong(String latlong) {
			String[] parts = latlong.split(",");
			try {
				if (parts.length == 2) {
					return new BigDecimal[] { new BigDecimal(parts[0].trim()), new BigDecimal(parts[1].trim()) };
				}
			}
			catch (NumberFormatException ignored) {
				// fall through: location is optional
			}
			return new BigDecimal[] { null, null };
		}
	}

	/** 522: business details. Shop name and address go to the network profile. Success 1567. */
	@Component
	static class Business implements EpsHandler {

		private final OnboardingSubjects subjects;
		private final AgentOnboardingApi onboarding;
		private final NetworkCommands network;
		private final UserDirectory users;

		Business(OnboardingSubjects subjects, AgentOnboardingApi onboarding, NetworkCommands network, UserDirectory users) {
			this.subjects = subjects;
			this.onboarding = onboarding;
			this.network = network;
			this.users = users;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(522));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			EpsParams p = call.params();
			OnboardingSubject s = subjects.resolve(call);
			String businessName = p.requireString("name");
			String signatory = p.requireString("authorized_signatory_name");
			String pincode = p.requireString("current_address_pincode");
			if (!pincode.matches("[1-9]\\d{5}")) {
				throw ValidationException.field("current_address_pincode", "Enter a valid 6-digit pincode");
			}
			var user = users.getById(s.orgId(), s.userId());
			onboarding.saveBusiness(s, new BusinessDetails(p.string("company_type").orElse(null), signatory,
					p.string("contact_person_cell").orElse(null), p.string("alternate_mobile").orElse(null)));
			network.updateShop(call.caller(), user.userCode(), new ShopView(businessName, null,
					p.requireString("current_address_line1"), p.string("current_address_line2").orElse(null),
					p.string("current_address_district").orElse(null), p.string("current_address_state").orElse(null), pincode,
					"India", null, null, null));
			if (user.name().startsWith("Agent ")) {
				network.updatePersonal(call.caller(), user.userCode(), signatory, user.personal());
			}
			return EpsEnvelope.ok().type(1567).message("Business details saved").build();
		}
	}

	/** 454: settlement bank account (completed by the passbook upload). Success 1445. */
	@Component
	static class Bank implements EpsHandler {

		private final OnboardingSubjects subjects;
		private final AgentOnboardingApi onboarding;

		Bank(OnboardingSubjects subjects, AgentOnboardingApi onboarding) {
			this.subjects = subjects;
			this.onboarding = onboarding;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(454));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			EpsParams p = call.params();
			onboarding.saveBankAccount(subjects.resolve(call), new BankAccountDetails(p.string("bank_code").orElse(null),
					p.string("bank_id").orElse(null), p.requireString("ifsc"), p.requireString("account")));
			return EpsEnvelope.ok().type(1445).message("Bank account saved").build();
		}
	}

	/** tf POST /karza/digilocker-redirection-url: start DigiLocker consent. */
	@Component
	static class DigiLockerLink implements EpsHandler {

		private final OnboardingSubjects subjects;
		private final DigiLockerProvider digiLocker;

		DigiLockerLink(OnboardingSubjects subjects, DigiLockerProvider digiLocker) {
			this.subjects = subjects;
			this.digiLocker = digiLocker;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.tf("/ekoicici/v1/marketuat", HttpMethod.POST, "/karza/digilocker-redirection-url"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			OnboardingSubject s = subjects.resolve(call);
			String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
			DigiLockerProvider.DigiLockerSession session = digiLocker.start(s.userId(), base);
			return EpsEnvelope.ok().data(Map.of("link", session.link(), "requestId", session.requestId())).build();
		}
	}

	/** 552: DigiLocker done. Success 1621. */
	@Component
	static class DigiLockerDone implements EpsHandler {

		private final OnboardingSubjects subjects;
		private final AgentOnboardingApi onboarding;
		private final DigiLockerProvider digiLocker;

		DigiLockerDone(OnboardingSubjects subjects, AgentOnboardingApi onboarding, DigiLockerProvider digiLocker) {
			this.subjects = subjects;
			this.onboarding = onboarding;
			this.digiLocker = digiLocker;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(552));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			String requestId = call.params().requireString("token_id");
			if (!"Y".equalsIgnoreCase(call.params().string("is_consent").orElse("Y"))) {
				throw ValidationException.field("is_consent", "Consent is required");
			}
			if (!digiLocker.isCompleted(requestId)) {
				throw new ConflictException("DIGILOCKER_PENDING", "DigiLocker verification is not complete yet.");
			}
			onboarding.completeDigiLocker(subjects.resolve(call), requestId);
			return EpsEnvelope.ok().type(1621).message("DigiLocker verified").build();
		}
	}

	/** 287: start agreement signing. 1069 = already signed (client shows Proceed), 1613 = sign at short_url. */
	@Component
	static class AgreementStart implements EpsHandler {

		private final OnboardingSubjects subjects;
		private final EsignProvider esign;

		AgreementStart(OnboardingSubjects subjects, EsignProvider esign) {
			this.subjects = subjects;
			this.esign = esign;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(287));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			OnboardingSubject s = subjects.resolve(call);
			EsignProvider.EsignSession session = esign.prepare(s.userId(), call.params().string("agreement_id").orElse("5"));
			if (session.alreadySigned()) {
				return EpsEnvelope.ok().type(1069).data(Map.of("document_id", session.documentId())).build();
			}
			return EpsEnvelope.ok().type(1613).data(Map.of("short_url", session.signingUrl(), "document_id",
					session.documentId(), "pipe", session.pipe())).build();
		}
	}

	/** 293: agreement signed. Verified with the provider, never trusted from the client. Success 1615. */
	@Component
	static class AgreementDone implements EpsHandler {

		private final OnboardingSubjects subjects;
		private final AgentOnboardingApi onboarding;
		private final EsignProvider esign;

		AgreementDone(OnboardingSubjects subjects, AgentOnboardingApi onboarding, EsignProvider esign) {
			this.subjects = subjects;
			this.onboarding = onboarding;
			this.esign = esign;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(293));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			String documentId = call.params().requireString("document_id");
			if (!esign.isSigned(documentId)) {
				throw new ConflictException("ESIGN_PENDING", "The agreement is not signed yet.");
			}
			onboarding.signAgreement(subjects.resolve(call), documentId);
			return EpsEnvelope.ok().type(1615).message("Agreement signed").build();
		}
	}
}
