package in.eko.eloka.onboarding.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drives the applicant API like a real client (token, ETag, Idempotency-Key, re-auth) for the
 * onboarding integration tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
abstract class OnboardingTestSupport {

	private static final byte[] PNG = Base64.getDecoder()
		.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

	@Autowired MockMvcTester mvc;
	@Autowired JsonMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired ApplicationService applicationService;
	@Autowired ExpiryJob expiryJob;

	/** A logged-in applicant: token, application id and the latest ETag. */
	final class Session {

		final String token;
		final String id;
		String etag;

		Session(String token, String id, String etag) {
			this.token = token;
			this.id = id;
			this.etag = etag;
		}

		String path(String suffix) {
			return "/v1/onboarding/applications/" + id + suffix;
		}

		/** Sends JSON with token, If-Match and a fresh Idempotency-Key; keeps the new ETag. */
		JsonNode send(HttpMethod method, String suffix, Object body, int expectedStatus) throws Exception {
			MvcTestResult result = exchange(method, suffix, body, UUID.randomUUID().toString(), null);
			assertThat(result.getResponse().getStatus()).as(method + " " + suffix + " → " + result.getResponse().getContentAsString())
				.isEqualTo(expectedStatus);
			return read(result);
		}

		MvcTestResult exchange(HttpMethod method, String suffix, Object body, String idemKey, String reauth) throws Exception {
			var request = mvc.method(method).uri(suffix.startsWith("/v1") ? suffix : path(suffix))
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body == null ? "{}" : json.writeValueAsString(body));
			if (etag != null) {
				request = request.header("If-Match", etag);
			}
			if (idemKey != null) {
				request = request.header("Idempotency-Key", idemKey);
			}
			if (reauth != null) {
				request = request.header("X-Reauth-Token", reauth);
			}
			MvcTestResult result = request.exchange();
			String newEtag = result.getResponse().getHeader("ETag");
			if (newEtag != null) {
				etag = newEtag;
			}
			else if (result.getResponse().getStatus() < 300) {
				refresh(); // calls without an ETag (e.g. A13) still change the version
			}
			return result;
		}

		JsonNode submitStep(String step, Map<String, ?> data) throws Exception {
			return send(HttpMethod.POST, "/steps/" + step + "/submit", Map.of("data", data), 200);
		}

		String upload(String docType) throws Exception {
			MvcTestResult result = mvc.post()
				.uri(path("/documents"))
				.multipart()
				.file(new MockMultipartFile("file", docType.toLowerCase() + ".png", "image/png", uniquePng()))
				.param("doc_type", docType)
				.header("Authorization", "Bearer " + token)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.exchange();
			assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
			refresh();
			return read(result).get("document_id").asString();
		}

		String reauth() throws Exception {
			assertThat(mvc.post().uri("/v1/onboarding/reauth").header("Authorization", "Bearer " + token).exchange()
				.getResponse().getStatus()).isEqualTo(202);
			MvcTestResult verified = mvc.post().uri("/v1/onboarding/reauth/verify").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"123456\"}").exchange();
			return read(verified).get("reauth_token").asString();
		}

		JsonNode snapshot() throws Exception {
			MvcTestResult result = mvc.get().uri(path("")).header("Authorization", "Bearer " + token).exchange();
			etag = result.getResponse().getHeader("ETag");
			return read(result);
		}

		void refresh() throws Exception {
			snapshot();
		}

		String stepStatus(String step) throws Exception {
			for (JsonNode node : snapshot().get("steps")) {
				if (node.get("step_key").asString().equals(step)) {
					return node.get("status").asString();
				}
			}
			throw new AssertionError("no step " + step);
		}
	}

	private static int pngCounter;

	/** A valid PNG with a unique trailing chunk, so uploads are distinct files. */
	static byte[] uniquePng() {
		byte[] bytes = java.util.Arrays.copyOf(PNG, PNG.length + 4);
		int n = ++pngCounter;
		bytes[PNG.length] = (byte) (n >> 24);
		bytes[PNG.length + 1] = (byte) (n >> 16);
		bytes[PNG.length + 2] = (byte) (n >> 8);
		bytes[PNG.length + 3] = (byte) n;
		return bytes;
	}

	JsonNode read(MvcTestResult result) throws Exception {
		String body = result.getResponse().getContentAsString();
		return body.isEmpty() ? json.createObjectNode() : json.readTree(body);
	}

	JsonNode postPublic(String uri, Object body, String idemKey) throws Exception {
		var request = mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
		if (idemKey != null) {
			request = request.header("Idempotency-Key", idemKey);
		}
		return read(request.exchange());
	}

	Session login(String mobile) throws Exception {
		JsonNode sent = postPublic("/v1/onboarding/otp", Map.of("mobile", mobile), UUID.randomUUID().toString());
		assertThat(sent.get("otp_ref").asString()).startsWith("otpref_");
		MvcTestResult verified = mvc.post().uri("/v1/onboarding/otp/verify").contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(Map.of("otp_ref", sent.get("otp_ref").asString(), "otp", "123456"))).exchange();
		assertThat(verified.getResponse().getStatus()).as(verified.getResponse().getContentAsString()).isEqualTo(200);
		JsonNode body = read(verified);
		return new Session(body.get("access_token").asString(), body.at("/application/onboarding_id").asString(),
				verified.getResponse().getHeader("ETag"));
	}

	/** Consent + program (Pvt Ltd) + PAN; returns the session ready for business details. */
	Session throughPan(String mobile, String pan) throws Exception {
		Session s = login(mobile);
		s.submitStep("CONSENT", Map.of("consent_version", "2026-10-01", "terms_version", "2026-10-01", "purposes",
				List.of("KYC", "SERVICE"), "accepted", true));
		s.submitStep("PROGRAM_SELECTION", Map.of("applicant_category", "ENTERPRISE", "product_type", "WHITE_LABEL",
				"entity_type", 1));
		JsonNode panResult = s.send(HttpMethod.POST, "/pan-verifications", Map.of("pan", pan), 200);
		assertThat(panResult.get("status").asString()).isEqualTo("SUCCEEDED");
		return s;
	}

	Map<String, Object> businessDetails(String signatureId) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("name", "Demo Enterprise Private Limited");
		data.put("company_type", "Private Limited");
		data.put("authorized_signatory_name", "Shlok Goswami");
		data.put("contact_person_cell", "9876543210");
		data.put("current_address_line1", "F-222 South Delhi Road");
		data.put("current_address_district", "New Delhi");
		data.put("current_address_state", "National Capital Territory of Delhi (UT)");
		data.put("current_address_pincode", "110044");
		data.put("signature_document_id", signatureId);
		return data;
	}

	/** The whole applicant journey up to SUBMITTED; returns the session. */
	Session submittedApplication(String mobile, String pan, String subdomain) throws Exception {
		Session s = throughPan(mobile, pan);
		s.submitStep("BUSINESS_DETAILS", businessDetails(s.upload("SIGNATURE")));
		JsonNode email = s.send(HttpMethod.POST, "/email-verifications", Map.of("email", "admin@" + subdomain + ".example"), 202);
		s.send(HttpMethod.POST, "/email-verifications/" + email.get("verification_id").asString() + "/confirm",
				Map.of("otp", "123456"), 200);
		JsonNode kyc = s.send(HttpMethod.POST, "/kyc-sessions", Map.of("method", "DIGILOCKER"), 201);
		s.send(HttpMethod.GET, "/kyc-sessions/" + kyc.get("session_id").asString(), null, 200);
		s.submitStep("LIVE_PHOTO", Map.of("document_id", s.upload("LIVE_PHOTO")));
		s.upload("ADDRESS_PROOF");
		s.upload("INCORPORATION_CERTIFICATE");
		s.submitStep("DOCUMENTS", Map.of());
		addBank(s, accountFor(mobile));
		s.submitStep("WHITE_LABEL_SETUP", Map.of("app_name", "Test " + subdomain, "subdomain_label", subdomain));
		s.submitStep("DECLARATION", Map.of("declaration_version", "2026-10-01", "accepted", true));
		signAgreement(s);
		assertThat(s.snapshot().get("status").asString()).isEqualTo("SUBMITTED");
		return s;
	}

	/** A unique 12-digit account per applicant (accounts ending 1111 come back with another holder name). */
	static String accountFor(String mobile) {
		return "12" + mobile;
	}

	void addBank(Session s, String account) throws Exception {
		String proof = s.upload("BANK_PROOF");
		MvcTestResult result = s.exchange(HttpMethod.POST, "/bank-accounts", Map.of("bank_code", "SBIN", "bank_id", "108",
				"ifsc", "SBIN0001234", "account", account, "proof_document_id", proof), UUID.randomUUID().toString(), s.reauth());
		assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
	}

	/** Prepares (or reuses) the agreement, signs it if needed and asks for the status. */
	JsonNode signAgreement(Session s) throws Exception {
		JsonNode agreement = s.send(HttpMethod.POST, "/agreements", null, 201);
		String agreementId = agreement.get("agreement_id").asString();
		if (!"SIGNED".equals(agreement.get("status").asString())) {
			MvcTestResult esign = s.exchange(HttpMethod.POST, "/agreements/" + agreementId + "/esign-sessions",
					Map.of("platform", "web"), UUID.randomUUID().toString(), s.reauth());
			assertThat(esign.getResponse().getStatus()).as(esign.getResponse().getContentAsString()).isEqualTo(201);
		}
		return s.send(HttpMethod.GET, "/agreements/" + agreementId, null, 200);
	}
}
