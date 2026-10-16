package in.eko.eloka.onboarding.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import in.eko.eloka.onboarding.EntityType;
import in.eko.eloka.onboarding.OnboardingProperties;
import in.eko.eloka.onboarding.StepKey;
import in.eko.eloka.shared.crypto.FieldCipher;
import in.eko.eloka.shared.crypto.Masking;
import in.eko.eloka.shared.crypto.Mobiles;
import in.eko.eloka.shared.error.ApiProblem;
import in.eko.eloka.verification.NameMatch;
import in.eko.eloka.whitelabel.BrandingDraftInput;
import in.eko.eloka.whitelabel.SubdomainReservation;
import in.eko.eloka.whitelabel.WhiteLabelApi;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The A8 data steps (API_SPECIFICATION.md §3.2, "Step payloads for A8"). */
final class StepHandlers {

	private StepHandlers() {
	}

	/** Consent to processing and the terms, at the current versions (DPDP Act). */
	@Component
	static class Consent implements StepHandler {

		private final ConsentRecordRepository consents;
		private final OnboardingProperties properties;
		private final JsonMapper json;

		Consent(ConsentRecordRepository consents, OnboardingProperties properties, JsonMapper json) {
			this.consents = consents;
			this.properties = properties;
			this.json = json;
		}

		@Override
		public StepKey key() {
			return StepKey.CONSENT;
		}

		@Override
		public Set<String> draftFields() {
			return Set.of();
		}

		@Override
		public void submit(StepContext context, JsonNode data) {
			StepData d = new StepData(data);
			String consentVersion = d.required("consent_version");
			String termsVersion = d.required("terms_version");
			d.isTrue("accepted");
			List<String> purposes = d.strings("purposes");
			if (purposes.isEmpty()) {
				d.error("purposes", "List what the applicant agrees to");
			}
			d.validate();
			if (!properties.consentVersion().equals(consentVersion) || !properties.termsVersion().equals(termsVersion)) {
				throw new ApiProblem(HttpStatus.UNPROCESSABLE_CONTENT, "CONSENT_VERSION_STALE",
						"The terms have changed. Please read and accept the latest version.",
						Map.of("consent_version", properties.consentVersion(), "terms_version", properties.termsVersion()));
			}
			consents.save(new ConsentRecord(context.applicationId(), consentVersion, termsVersion,
					json.writeValueAsString(purposes), context.now()));
			Map<String, Object> summary = new LinkedHashMap<>();
			summary.put("consent_version", consentVersion);
			summary.put("terms_version", termsVersion);
			context.complete(key(), json.writeValueAsString(summary));
		}
	}

	/** Enterprise + White Label portal + legal form (today's Connect steps 2 and 4). */
	@Component
	static class ProgramSelection implements StepHandler {

		private final JsonMapper json;

		ProgramSelection(JsonMapper json) {
			this.json = json;
		}

		@Override
		public StepKey key() {
			return StepKey.PROGRAM_SELECTION;
		}

		@Override
		public Set<String> draftFields() {
			return Set.of("applicant_category", "product_type", "entity_type");
		}

		@Override
		public void submit(StepContext context, JsonNode data) {
			StepData d = new StepData(data);
			String category = d.required("applicant_category");
			String product = d.required("product_type");
			Integer entityCode = d.integer("entity_type");
			d.validate();
			if (!"ENTERPRISE".equals(category)) {
				d.error("applicant_category", "Only ENTERPRISE can apply here");
			}
			// OQ-B11: Partner APIs keep their existing flow
			if (!"WHITE_LABEL".equals(product)) {
				d.error("product_type", "Only WHITE_LABEL is offered here; Partner APIs have their own sign-up");
			}
			EntityType entityType = EntityType.fromCode(entityCode).orElse(null);
			if (entityType == null) {
				d.error("entity_type", "Use 1 (Pvt Ltd), 2 (LLP), 3 (Partnership) or 4 (Sole proprietorship)");
			}
			d.validate();
			context.application().selectProgram(category, product, entityType);
			Map<String, Object> summary = new LinkedHashMap<>();
			summary.put("applicant_category", category);
			summary.put("product_type", product);
			summary.put("entity_type", entityType.code());
			String summaryJson = json.writeValueAsString(summary);
			// The summary holds the exact choice, so an equal summary is the same program
			context.complete(key(), summaryJson, context.sameSummary(key(), summaryJson));
		}
	}

	/** Business details, signatory and specimen signature (frontend BusinessDetailsStep fields). */
	@Component
	static class BusinessDetails implements StepHandler {

		private final ApplicationDocuments documents;
		private final FieldCipher cipher;
		private final JsonMapper json;

		BusinessDetails(ApplicationDocuments documents, FieldCipher cipher, JsonMapper json) {
			this.documents = documents;
			this.cipher = cipher;
			this.json = json;
		}

		@Override
		public StepKey key() {
			return StepKey.BUSINESS_DETAILS;
		}

		@Override
		public Set<String> draftFields() {
			// Mobile numbers are personal data: not kept in drafts
			return Set.of("name", "company_type", "authorized_signatory_name", "current_address_line1",
					"current_address_line2", "current_address_district", "current_address_state", "current_address_pincode",
					"gstin", "cin");
		}

		@Override
		public void submit(StepContext context, JsonNode data) {
			StepData d = new StepData(data);
			String name = d.matching("name", StepData.COMPANY_NAME, "2–100 letters, digits, spaces and , . / : & ' -", true);
			String companyType = d.length("company_type", 1, 40, true);
			String signatory = d.matching("authorized_signatory_name", StepData.PERSON_NAME,
					"Letters only, up to three words", true);
			String contact = d.matching("contact_person_cell", StepData.MOBILE, "Enter a valid 10-digit mobile number", true);
			String alternate = d.matching("alternate_mobile", StepData.MOBILE, "Enter a valid 10-digit mobile number", false);
			String line1 = d.length("current_address_line1", 10, 200, true);
			String line2 = d.length("current_address_line2", 1, 200, false);
			String district = d.length("current_address_district", 2, 100, true);
			String state = d.length("current_address_state", 2, 100, true);
			String pincode = d.matching("current_address_pincode", StepData.PINCODE, "Enter a valid 6-digit pincode", true);
			String gstin = upper(d.matching("gstin", StepData.GSTIN, "Enter a valid GSTIN", false));
			String cin = upper(d.matching("cin", StepData.CIN, "Enter a valid CIN", false));
			String signatureId = d.documentId("signature_document_id", true);
			d.validate();

			ApplicationDocument signature = documents.requireCurrent(context.applicationId(), signatureId, "SIGNATURE");
			BusinessProfile profile = context.profile();
			BusinessDetailsInput input = new BusinessDetailsInput(name, companyType, signatory, contact, alternate, line1,
					line2, district, state, pincode, gstin, cin, signature.getStoredDocumentId());
			String normalizedContact = Mobiles.normalize(contact);
			profile.details(input, cipher.encrypt(normalizedContact), Masking.mobile(normalizedContact),
					alternate == null ? null : cipher.encrypt(Mobiles.normalize(alternate)));

			// E9: a name that does not match the PAN is allowed but flagged for the reviewer
			int score = NameMatch.score(name, profile.getNameAsPerPan());
			Map<String, Object> summary = new LinkedHashMap<>();
			summary.put("name", name);
			summary.put("authorized_signatory_name", signatory);
			summary.put("contact_mobile_masked", Masking.mobile(normalizedContact));
			summary.put("current_address_pincode", pincode);
			summary.put("signature_document_id", signatureId);
			summary.put("name_matches_pan", score >= NameMatch.MATCH_THRESHOLD);
			summary.put("name_match_score", score);
			context.complete(key(), json.writeValueAsString(summary));
		}

		private static String upper(String value) {
			return value == null ? null : value.toUpperCase(java.util.Locale.ROOT);
		}
	}

	/** Live photo of the signatory (OQ-P4: on by default). Face match needs a provider (later). */
	@Component
	static class LivePhoto implements StepHandler {

		private final ApplicationDocuments documents;
		private final JsonMapper json;

		LivePhoto(ApplicationDocuments documents, JsonMapper json) {
			this.documents = documents;
			this.json = json;
		}

		@Override
		public StepKey key() {
			return StepKey.LIVE_PHOTO;
		}

		@Override
		public Set<String> draftFields() {
			return Set.of();
		}

		@Override
		public void submit(StepContext context, JsonNode data) {
			StepData d = new StepData(data);
			String documentId = d.documentId("document_id", true);
			String latlong = d.length("captured_latlong", 3, 60, false);
			d.validate();
			ApplicationDocument photo = documents.requireCurrent(context.applicationId(), documentId, "LIVE_PHOTO");
			context.profile().livePhoto(photo.getStoredDocumentId(), latlong);
			Map<String, Object> summary = new LinkedHashMap<>();
			summary.put("document_id", documentId);
			summary.put("location_captured", latlong != null);
			context.complete(key(), json.writeValueAsString(summary));
		}
	}

	/** Completion check: every document type the entity type needs is uploaded (OQ-P5 list in config). */
	@Component
	static class Documents implements StepHandler {

		private final ApplicationDocuments documents;
		private final OnboardingProperties properties;
		private final JsonMapper json;

		Documents(ApplicationDocuments documents, OnboardingProperties properties, JsonMapper json) {
			this.documents = documents;
			this.properties = properties;
			this.json = json;
		}

		@Override
		public StepKey key() {
			return StepKey.DOCUMENTS;
		}

		@Override
		public Set<String> draftFields() {
			return Set.of();
		}

		@Override
		public void submit(StepContext context, JsonNode data) {
			List<String> required = properties.requiredDocuments()
				.getOrDefault(context.application().getEntityType(), List.of());
			Set<String> uploaded = documents.currentTypes(context.applicationId());
			List<String> missing = required.stream().filter(type -> !uploaded.contains(type)).toList();
			if (!missing.isEmpty()) {
				throw new ApiProblem(HttpStatus.CONFLICT, "PREREQUISITE_INCOMPLETE", "Upload the missing documents first",
						Map.of("missing_documents", missing));
			}
			context.complete(key(), json.writeValueAsString(Map.of("documents", required)));
		}
	}

	/** App name, sub-domain (reserved atomically), optional logo and colours (OQ-P8). */
	@Component
	static class WhiteLabelSetup implements StepHandler {

		private static final Set<String> THEME_KEYS = Set.of("primary", "primary_light", "primary_dark", "accent",
				"accent_light", "accent_dark", "navstyle");
		private static final java.util.regex.Pattern COLOUR = java.util.regex.Pattern.compile("^#[0-9a-fA-F]{6}$");

		private final WhiteLabelApi whiteLabel;
		private final ApplicationDocuments documents;
		private final JsonMapper json;

		WhiteLabelSetup(WhiteLabelApi whiteLabel, ApplicationDocuments documents, JsonMapper json) {
			this.whiteLabel = whiteLabel;
			this.documents = documents;
			this.json = json;
		}

		@Override
		public StepKey key() {
			return StepKey.WHITE_LABEL_SETUP;
		}

		@Override
		public Set<String> draftFields() {
			return Set.of("app_name", "subdomain_label", "theme");
		}

		@Override
		public void submit(StepContext context, JsonNode data) {
			StepData d = new StepData(data);
			String appName = d.matching("app_name", StepData.COMPANY_NAME, "2–60 letters, digits and spaces", true);
			if (appName != null && appName.length() > 60) {
				d.error("app_name", "At most 60 characters");
			}
			String label = d.required("subdomain_label");
			String logoId = d.documentId("logo_document_id", false);
			Map<String, String> theme = new LinkedHashMap<>();
			JsonNode themeNode = d.object("theme");
			if (themeNode != null) {
				for (String colourKey : THEME_KEYS) {
					JsonNode value = themeNode.get(colourKey);
					if (value == null || value.isNull()) {
						continue;
					}
					String text = value.asString();
					boolean valid = "navstyle".equals(colourKey) ? text.matches("^(dark|light)$") : COLOUR.matcher(text).matches();
					if (!valid) {
						d.error("theme." + colourKey, "navstyle" .equals(colourKey) ? "dark or light" : "Use #RRGGBB");
					}
					theme.put(colourKey, text);
				}
			}
			d.validate();
			java.util.UUID logoDocument = logoId == null ? null
					: documents.requireCurrent(context.applicationId(), logoId, "LOGO").getStoredDocumentId();
			SubdomainReservation reservation = whiteLabel.reserve(context.applicationId(), label);
			whiteLabel.saveBranding(context.applicationId(), new BrandingDraftInput(appName, logoDocument, theme));
			Map<String, Object> summary = new LinkedHashMap<>();
			summary.put("app_name", appName);
			summary.put("subdomain_label", reservation.label());
			summary.put("default_url", "https://" + reservation.hostname());
			summary.put("logo_document_id", logoId);
			summary.put("theme", theme);
			context.complete(key(), json.writeValueAsString(summary));
		}
	}

	/** The applicant confirms everything is true (today ops ticks this in the Go-Live tool). */
	@Component
	static class Declaration implements StepHandler {

		private final OnboardingProperties properties;
		private final JsonMapper json;

		Declaration(OnboardingProperties properties, JsonMapper json) {
			this.properties = properties;
			this.json = json;
		}

		@Override
		public StepKey key() {
			return StepKey.DECLARATION;
		}

		@Override
		public Set<String> draftFields() {
			return Set.of();
		}

		@Override
		public void submit(StepContext context, JsonNode data) {
			StepData d = new StepData(data);
			String version = d.required("declaration_version");
			d.isTrue("accepted");
			d.validate();
			if (!properties.declarationVersion().equals(version)) {
				throw new ApiProblem(HttpStatus.UNPROCESSABLE_CONTENT, "CONSENT_VERSION_STALE",
						"The declaration text has changed. Please read the latest version.",
						Map.of("declaration_version", properties.declarationVersion()));
			}
			context.complete(key(), json.writeValueAsString(Map.of("declaration_version", version)));
		}
	}
}
