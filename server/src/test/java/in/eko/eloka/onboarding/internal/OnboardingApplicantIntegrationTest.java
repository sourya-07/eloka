package in.eko.eloka.onboarding.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
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
 * P7 Local Admin applicant API end to end (docs/onboarding/P7_APPLICANT_API.md §7): OTP →
 * every step → signed agreement → SUBMITTED, plus the concurrency, idempotency, duplicate,
 * prerequisite, invalidation, blocking, withdraw and expiry rules.
 */
class OnboardingApplicantIntegrationTest extends OnboardingTestSupport {

	@Test
	void applicantGoesFromOtpToSubmitted() throws Exception {
		Session s = throughPan("9811100701", "ABCCD1234E");
		assertThat(s.stepStatus("BUSINESS_PAN")).isEqualTo("COMPLETED");

		JsonNode details = s.submitStep("BUSINESS_DETAILS", businessDetails(s.upload("SIGNATURE")));
		assertThat(details.get("next_recommended_step").asString()).isEqualTo("EMAIL_VERIFICATION");

		JsonNode email = s.send(HttpMethod.POST, "/email-verifications", Map.of("email", "admin@shlok.example"), 202);
		s.send(HttpMethod.POST, "/email-verifications/" + email.get("verification_id").asString() + "/confirm",
				Map.of("otp", "123456"), 200);

		JsonNode kyc = s.send(HttpMethod.POST, "/kyc-sessions", Map.of("method", "DIGILOCKER"), 201);
		assertThat(kyc.get("redirect_url").asString()).contains("/v1/dev/digilocker");
		JsonNode kycDone = s.send(HttpMethod.GET, "/kyc-sessions/" + kyc.get("session_id").asString(), null, 200);
		assertThat(kycDone.get("status").asString()).isEqualTo("SUCCEEDED");
		assertThat(kycDone.at("/result/aadhaar_masked").asString()).isEqualTo("XXXX-XXXX-1234");

		s.submitStep("LIVE_PHOTO", Map.of("document_id", s.upload("LIVE_PHOTO"), "captured_latlong", "28.5,77.2"));
		s.upload("ADDRESS_PROOF");
		s.upload("INCORPORATION_CERTIFICATE");
		s.submitStep("DOCUMENTS", Map.of());

		String proof = s.upload("BANK_PROOF");
		Map<String, Object> bank = Map.of("bank_code", "SBIN", "bank_id", "108", "ifsc", "SBIN0001234", "account",
				"123456789012", "proof_document_id", proof);
		MvcTestResult noReauth = s.exchange(HttpMethod.POST, "/bank-accounts", bank, UUID.randomUUID().toString(), null);
		assertThat(read(noReauth).get("code").asString()).isEqualTo("REAUTH_REQUIRED");
		MvcTestResult bankResult = s.exchange(HttpMethod.POST, "/bank-accounts", bank, UUID.randomUUID().toString(), s.reauth());
		assertThat(bankResult.getResponse().getStatus()).as(bankResult.getResponse().getContentAsString()).isEqualTo(200);
		assertThat(read(bankResult).get("account_masked").asString()).endsWith("9012");

		JsonNode label = s.send(HttpMethod.GET, "/v1/onboarding/subdomains/availability?label=shlok-p7", null, 200);
		assertThat(label.get("available").asBoolean()).isTrue();
		s.submitStep("WHITE_LABEL_SETUP", Map.of("app_name", "Shlok Enterprise", "subdomain_label", "shlok-p7", "theme",
				Map.of("primary", "#015a8c")));
		s.submitStep("DECLARATION", Map.of("declaration_version", "2026-10-01", "accepted", true));

		JsonNode agreement = s.send(HttpMethod.POST, "/agreements", null, 201);
		String agreementId = agreement.get("agreement_id").asString();
		assertThat(mvc.get().uri(s.path("/agreements/" + agreementId + "/pdf")).header("Authorization", "Bearer " + s.token)
			.exchange().getResponse().getContentType()).isEqualTo("application/pdf");
		MvcTestResult esign = s.exchange(HttpMethod.POST, "/agreements/" + agreementId + "/esign-sessions",
				Map.of("platform", "web"), UUID.randomUUID().toString(), s.reauth());
		assertThat(esign.getResponse().getStatus()).as(esign.getResponse().getContentAsString()).isEqualTo(201);

		JsonNode signed = s.send(HttpMethod.GET, "/agreements/" + agreementId, null, 200);
		assertThat(signed.get("status").asString()).isEqualTo("SIGNED");
		assertThat(signed.at("/application/status").asString()).isEqualTo("SUBMITTED");
		assertThat(s.stepStatus("BUSINESS_DETAILS")).isEqualTo("LOCKED");

		JsonNode timeline = s.send(HttpMethod.GET, "/timeline", null, 200);
		assertThat(timeline.get("items").findValuesAsString("event")).contains("ONBOARDING_STARTED", "STATUS_SUBMITTED");
		// After submit nothing can change
		s.refresh();
		s.send(HttpMethod.POST, "/steps/DECLARATION/submit", Map.of("data", Map.of()), 409);
	}

	@Test
	void loginAgainResumesTheSameApplication() throws Exception {
		Session first = login("9811100702");
		Session second = login("9811100702");
		assertThat(second.id).isEqualTo(first.id);
	}

	@Test
	void writesNeedTheCurrentVersion() throws Exception {
		Session s = login("9811100703");
		String stale = s.etag;
		s.submitStep("CONSENT", Map.of("consent_version", "2026-10-01", "terms_version", "2026-10-01", "purposes",
				List.of("KYC"), "accepted", true));
		s.etag = stale;
		JsonNode conflict = s.send(HttpMethod.POST, "/steps/PROGRAM_SELECTION/submit", Map.of("data", Map.of()), 412);
		assertThat(conflict.at("/current/onboarding_id").asString()).isEqualTo(s.id);
		s.etag = null;
		assertThat(s.send(HttpMethod.POST, "/steps/PROGRAM_SELECTION/submit", Map.of("data", Map.of()), 428)
			.get("code").asString()).isEqualTo("PRECONDITION_REQUIRED");
	}

	@Test
	void idempotencyKeyReplaysAndRefusesReuse() throws Exception {
		Session s = login("9811100704");
		String key = UUID.randomUUID().toString();
		Map<String, Object> consent = Map.of("data", Map.of("consent_version", "2026-10-01", "terms_version", "2026-10-01",
				"purposes", List.of("KYC"), "accepted", true));
		String etag = s.etag;
		MvcTestResult first = s.exchange(HttpMethod.POST, "/steps/CONSENT/submit", consent, key, null);
		s.etag = etag;
		MvcTestResult replay = s.exchange(HttpMethod.POST, "/steps/CONSENT/submit", consent, key, null);
		assertThat(replay.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
		assertThat(replay.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
		MvcTestResult reused = s.exchange(HttpMethod.POST, "/steps/CONSENT/submit", Map.of("data", Map.of()), key, null);
		assertThat(read(reused).get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
		MvcTestResult missing = s.exchange(HttpMethod.POST, "/steps/CONSENT/submit", consent, null, null);
		assertThat(read(missing).get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
	}

	@Test
	void stepsNeedTheirPrerequisites() throws Exception {
		Session s = login("9811100705");
		JsonNode early = s.send(HttpMethod.POST, "/pan-verifications", Map.of("pan", "ABCCD1234E"), 409);
		assertThat(early.get("code").asString()).isEqualTo("PREREQUISITE_INCOMPLETE");
		assertThat(early.get("missing_steps").toString()).contains("PROGRAM_SELECTION");
	}

	@Test
	void aPanCanBelongToOnlyOneLiveApplication() throws Exception {
		throughPan("9811100706", "PQRCS6789K");
		Session other = login("9811100707");
		other.submitStep("CONSENT", Map.of("consent_version", "2026-10-01", "terms_version", "2026-10-01", "purposes",
				List.of("KYC"), "accepted", true));
		other.submitStep("PROGRAM_SELECTION", Map.of("applicant_category", "ENTERPRISE", "product_type", "WHITE_LABEL",
				"entity_type", 1));
		assertThat(other.send(HttpMethod.POST, "/pan-verifications", Map.of("pan", "PQRCS6789K"), 409).get("code").asString())
			.isEqualTo("PAN_ALREADY_REGISTERED");
		assertThat(other.send(HttpMethod.POST, "/pan-verifications", Map.of("pan", "PQRPS6789K"), 422).get("code").asString())
			.isEqualTo("PAN_HOLDER_TYPE_NOT_ALLOWED");
	}

	@Test
	void changingThePanResetsTheStepsBuiltOnIt() throws Exception {
		Session s = throughPan("9811100708", "LMNCA1111B");
		s.submitStep("BUSINESS_DETAILS", businessDetails(s.upload("SIGNATURE")));
		assertThat(s.stepStatus("BUSINESS_DETAILS")).isEqualTo("COMPLETED");
		s.send(HttpMethod.POST, "/pan-verifications", Map.of("pan", "LMNCA2222B"), 200);
		assertThat(s.stepStatus("BUSINESS_DETAILS")).isEqualTo("IN_PROGRESS");
	}

	@Test
	void failedVerificationsCountAndThenBlock() throws Exception {
		Session s = login("9811100709");
		s.submitStep("CONSENT", Map.of("consent_version", "2026-10-01", "terms_version", "2026-10-01", "purposes",
				List.of("KYC"), "accepted", true));
		s.submitStep("PROGRAM_SELECTION", Map.of("applicant_category", "ENTERPRISE", "product_type", "WHITE_LABEL",
				"entity_type", 1));
		for (int attempt = 1; attempt <= 3; attempt++) {
			JsonNode failed = s.send(HttpMethod.POST, "/pan-verifications", Map.of("pan", "ABCC" + (char) ('A' + attempt) + "0000E"), 422);
			assertThat(failed.get("code").asString()).isEqualTo("VERIFICATION_FAILED");
		}
		assertThat(s.stepStatus("BUSINESS_PAN")).isEqualTo("BLOCKED");
		assertThat(s.send(HttpMethod.POST, "/pan-verifications", Map.of("pan", "ABCCD1234E"), 423).get("code").asString())
			.isEqualTo("STEP_BLOCKED");
	}

	@Test
	void withdrawNeedsReauthAndReleasesTheSubdomain() throws Exception {
		Session s = login("9811100710");
		s.submitStep("CONSENT", Map.of("consent_version", "2026-10-01", "terms_version", "2026-10-01", "purposes",
				List.of("KYC"), "accepted", true));
		s.submitStep("PROGRAM_SELECTION", Map.of("applicant_category", "ENTERPRISE", "product_type", "WHITE_LABEL",
				"entity_type", 4));
		s.submitStep("WHITE_LABEL_SETUP", Map.of("app_name", "Withdraw Me", "subdomain_label", "withdraw-me"));

		Session rival = login("9811100711");
		rival.submitStep("CONSENT", Map.of("consent_version", "2026-10-01", "terms_version", "2026-10-01", "purposes",
				List.of("KYC"), "accepted", true));
		rival.submitStep("PROGRAM_SELECTION", Map.of("applicant_category", "ENTERPRISE", "product_type", "WHITE_LABEL",
				"entity_type", 4));
		assertThat(rival.send(HttpMethod.POST, "/steps/WHITE_LABEL_SETUP/submit",
				Map.of("data", Map.of("app_name", "Rival", "subdomain_label", "withdraw-me")), 409).get("code").asString())
			.isEqualTo("SUBDOMAIN_TAKEN");

		MvcTestResult refused = s.exchange(HttpMethod.POST, "/withdraw", Map.of("reason_code", "CHANGED_MIND"),
				UUID.randomUUID().toString(), null);
		assertThat(read(refused).get("code").asString()).isEqualTo("REAUTH_REQUIRED");
		MvcTestResult withdrawn = s.exchange(HttpMethod.POST, "/withdraw", Map.of("reason_code", "CHANGED_MIND"),
				UUID.randomUUID().toString(), s.reauth());
		assertThat(read(withdrawn).get("status").asString()).isEqualTo("WITHDRAWN");
		assertThat(rival.send(HttpMethod.GET, "/v1/onboarding/subdomains/availability?label=withdraw-me", null, 200)
			.get("available").asBoolean()).isTrue();
	}

	@Test
	void idleApplicationsExpire() throws Exception {
		Session s = login("9811100712");
		jdbc.update("UPDATE onboarding_application SET last_applicant_activity_at = NOW(6) - INTERVAL 31 DAY WHERE public_id = ?",
				s.id);
		expiryJob.run();
		assertThat(s.snapshot().get("status").asString()).isEqualTo("EXPIRED");
		assertThat(login("9811100712").id).isNotEqualTo(s.id);
	}

	@Test
	void applicantAndAgentTokensStayInTheirOwnArea() throws Exception {
		Session s = login("9811100713");
		MvcTestResult eps = mvc.post().uri("/").header("Authorization", "Bearer " + s.token)
			.contentType(MediaType.APPLICATION_JSON).content("{\"interaction_type_id\":\"151\",\"mobile\":\"9811100713\"}")
			.exchange();
		assertThat(read(eps).path("status").asInt(1)).isNotZero();
		assertThat(mvc.get().uri("/v1/onboarding/applications/current").exchange().getResponse().getStatus()).isEqualTo(401);
	}
}
