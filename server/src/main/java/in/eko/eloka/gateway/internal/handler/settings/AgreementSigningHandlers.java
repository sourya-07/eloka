package in.eko.eloka.gateway.internal.handler.settings;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.documents.DocumentApi;
import in.eko.eloka.documents.DocumentPurpose;
import in.eko.eloka.documents.DocumentRef;
import in.eko.eloka.documents.NewDocument;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.gateway.internal.envelope.UploadedFile;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.network.NetworkCommands;
import in.eko.eloka.reference.ReferenceData;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.tenant.OrgSettings;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Business Settings → Agreement Signing: signature image, business email OTP, pincode lookup. */
final class AgreementSigningHandlers {

	static final String SIGNATORY = "authorized_signatory";
	private static final String V2 = "/ekoicici/v2";

	private AgreementSigningHandlers() {
	}

	/** tf GET v2 /network/agreement/recent-signature: the stored signature (base64) and signatory name. */
	@Component
	static class RecentSignature implements EpsHandler {

		private final OrgSettings settings;
		private final DocumentApi documents;
		private final JsonMapper json;

		RecentSignature(OrgSettings settings, DocumentApi documents, JsonMapper json) {
			this.settings = settings;
			this.documents = documents;
			this.json = json;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.tf(V2, HttpMethod.GET, "/network/agreement/recent-signature"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.ORG_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			return settings.get(caller.orgId(), SIGNATORY).map(json::readTree).map(node -> {
				byte[] image = documents.load(caller.orgId(), UUID.fromString(node.path("document_id").asString())).bytes();
				return EpsEnvelope.ok().data(Map.of("code", Base64.getEncoder().encodeToString(image),
						"authorized_signatory_name", node.path("name").asString())).build();
			}).orElseGet(() -> EpsEnvelope.ok().message("No signature yet").build());
		}
	}

	/** customupload tf POST v2 /network/agreement/upload-doc-sign-config: new signature image + signatory name. */
	@Component
	static class UploadSignature implements EpsHandler {

		private final OrgSettings settings;
		private final DocumentApi documents;
		private final JsonMapper json;

		UploadSignature(OrgSettings settings, DocumentApi documents, JsonMapper json) {
			this.settings = settings;
			this.documents = documents;
			this.json = json;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.tf(V2, HttpMethod.POST, "/network/agreement/upload-doc-sign-config"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.ORG_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			String name = call.params().requireString("authorized_signatory_name").trim();
			if (!name.matches("[A-Za-z ]{2,100}")) {
				throw ValidationException.field("authorized_signatory_name", "Use letters and spaces only");
			}
			UploadedFile file = call.files().stream().filter(f -> f.field().equals("file1")).findFirst()
				.orElseThrow(() -> ValidationException.field("file1", "Upload the signature image"));
			if (!isJpegOrPng(file.bytes())) {
				throw ValidationException.field("file1", "The signature must be a JPG or PNG image");
			}
			DocumentRef stored = documents.store(new NewDocument(caller.orgId(), caller.userId(), caller.userId(),
					DocumentPurpose.SIGNATURE, file.filename(), file.bytes()));
			JsonNode value = json.createObjectNode().put("name", name).put("document_id", stored.id().toString());
			settings.put(caller.orgId(), SIGNATORY, json.writeValueAsString(value), caller);
			return EpsEnvelope.ok().message("Signature saved").build();
		}
	}

	private static boolean isJpegOrPng(byte[] b) {
		boolean jpeg = b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
		boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
		return jpeg || png;
	}

	/** 553 / 554 send or resend, 555 verify: business email OTP ({@code intent_id 16}). */
	@Component
	static class EmailOtp implements EpsHandler {

		private static final String EMAIL_INTENT = "16";

		private final IdentityApi identity;
		private final NetworkCommands network;

		EmailOtp(IdentityApi identity, NetworkCommands network) {
			this.identity = identity;
			this.network = network;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(553), RouteKey.interaction(554), RouteKey.interaction(555));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			if (!EMAIL_INTENT.equals(call.params().string("intent_id").orElse(""))) {
				throw ValidationException.field("intent_id", "Only email verification is supported here");
			}
			Caller caller = call.caller();
			String email = call.params().requireString("email");
			if (!"555".equals(call.request().interactionId().orElse(""))) {
				identity.sendEmailOtp(caller.orgId(), email, call.request().clientIp());
				return EpsEnvelope.ok().message("OTP sent to your email").build();
			}
			if (!identity.verifyEmailOtp(caller.orgId(), email, call.params().requireString("otp"))) {
				return EpsEnvelope.fail("Wrong or expired OTP. Please try again.").build();
			}
			network.setVerifiedBusinessEmail(caller, email.trim().toLowerCase(java.util.Locale.ROOT));
			return EpsEnvelope.ok().message("Email verified").build();
		}
	}

	/** 353: state (and empty city) for a PIN code. {@code dependent_params} is top-level. */
	@Component
	static class Pincode implements EpsHandler {

		private final ReferenceData reference;

		Pincode(ReferenceData reference) {
			this.reference = reference;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(353));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.SESSION;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			String pincode = call.params().requireString("pincode");
			return reference.stateForPincode(pincode)
				.map(state -> EpsEnvelope.ok().put("dependent_params",
						List.of(Map.of("name", "sender_city", "value", ""), Map.of("name", "sender_state", "value", state))).build())
				.orElseGet(() -> EpsEnvelope.fail("Unknown PIN code").build());
		}
	}
}
