package in.eko.eloka.onboarding.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.identity.StaffRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** P8 staff review end to end (docs/onboarding/P8_STAFF_REVIEW.md §5). */
class StaffReviewIntegrationTest extends OnboardingTestSupport {

	@Autowired IdentityApi identity;

	private static boolean seeded;

	@BeforeEach
	void seedStaff() {
		if (seeded) {
			return;
		}
		identity.saveStaff("rita.reviewer@eko.co.in", "Rita Reviewer", "9700000001", EnumSet.of(StaffRole.REVIEWER), true);
		identity.saveStaff("anil.approver@eko.co.in", "Anil Approver", "9700000002", EnumSet.of(StaffRole.APPROVER), true);
		identity.saveStaff("ravi.reviewer@eko.co.in", "Ravi Reviewer", "9700000003", EnumSet.of(StaffRole.REVIEWER), true);
		identity.saveStaff("ada.admin@eko.co.in", "Ada Admin", "9700000004", EnumSet.of(StaffRole.ADMIN), true);
		seeded = true;
	}

	/** A staff session (local/test OTP login). */
	final class Staff {

		final String token;

		Staff(String token) {
			this.token = token;
		}

		JsonNode call(HttpMethod method, String path, Object body, int expected) throws Exception {
			MvcTestResult result = mvc.method(method).uri("/v1/admin" + path)
				.header("Authorization", "Bearer " + token)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body == null ? "{}" : json.writeValueAsString(body))
				.exchange();
			assertThat(result.getResponse().getStatus()).as(method + " " + path + " → " + result.getResponse().getContentAsString())
				.isEqualTo(expected);
			String text = result.getResponse().getContentAsString();
			return text.isEmpty() ? json.createObjectNode() : json.readTree(text);
		}

		JsonNode caseOf(String id) throws Exception {
			return call(HttpMethod.GET, "/applications/" + id, null, 200);
		}

		void verifyAllDocuments(String id) throws Exception {
			for (JsonNode document : caseOf(id).get("documents")) {
				if ("PENDING_REVIEW".equals(document.get("status").asString())) {
					call(HttpMethod.POST, "/applications/" + id + "/documents/" + document.get("document_id").asString()
							+ "/verify", Map.of(), 200);
				}
			}
		}
	}

	private Staff staff(String mobile) throws Exception {
		mvc.post().uri("/v1/admin/auth/otp").contentType(MediaType.APPLICATION_JSON)
			.content("{\"mobile\":\"" + mobile + "\"}").exchange();
		MvcTestResult verified = mvc.post().uri("/v1/admin/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"mobile\":\"" + mobile + "\",\"otp\":\"123456\"}").exchange();
		assertThat(verified.getResponse().getStatus()).as(verified.getResponse().getContentAsString()).isEqualTo(200);
		return new Staff(json.readTree(verified.getResponse().getContentAsString()).get("access_token").asString());
	}

	@Test
	void reviewerRecommendsAndADifferentApproverApproves() throws Exception {
		Session applicant = submittedApplication("9811120801", "ABCCE2345F", "staff-happy");
		Staff reviewer = staff("9700000001");
		Staff approver = staff("9700000002");

		JsonNode queue = reviewer.call(HttpMethod.GET, "/applications?status=SUBMITTED&q=" + applicant.id, null, 200);
		assertThat(queue.get("items").findValuesAsString("onboarding_id")).containsExactly(applicant.id);

		JsonNode claimed = reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		assertThat(claimed.at("/application/status").asString()).isEqualTo("UNDER_REVIEW");
		assertThat(claimed.get("approval_blockers").toString()).contains("NO_APPROVE_RECOMMENDATION", "DOCUMENTS_NOT_VERIFIED");

		reviewer.verifyAllDocuments(applicant.id);
		reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/recommendations",
				Map.of("recommendation", "APPROVE", "note", "All checks fine"), 200);
		assertThat(reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/approve", null, 403).get("code")
			.asString()).isEqualTo("FORBIDDEN");

		JsonNode approved = approver.call(HttpMethod.POST, "/applications/" + applicant.id + "/approve", Map.of("note", "ok"), 200);
		assertThat(approved.at("/application/status").asString()).isEqualTo("APPROVED");
		assertThat(applicant.snapshot().get("status").asString()).isEqualTo("APPROVED");
	}

	@Test
	void nobodyApprovesTheirOwnRecommendation() throws Exception {
		Session applicant = submittedApplication("9811120802", "ABCCF2345G", "staff-maker");
		Staff admin = staff("9700000004");
		admin.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		admin.verifyAllDocuments(applicant.id);
		admin.call(HttpMethod.POST, "/applications/" + applicant.id + "/recommendations", Map.of("recommendation", "APPROVE"), 200);
		assertThat(admin.call(HttpMethod.POST, "/applications/" + applicant.id + "/approve", null, 403).get("code").asString())
			.isEqualTo("MAKER_CHECKER_VIOLATION");
	}

	@Test
	void aBankNameMismatchNeedsADecisionBeforeApproval() throws Exception {
		Session applicant = throughPan("9811120803", "ABCCG2345H");
		applicant.submitStep("BUSINESS_DETAILS", businessDetails(applicant.upload("SIGNATURE")));
		JsonNode email = applicant.send(HttpMethod.POST, "/email-verifications", Map.of("email", "a@mismatch.example"), 202);
		applicant.send(HttpMethod.POST, "/email-verifications/" + email.get("verification_id").asString() + "/confirm",
				Map.of("otp", "123456"), 200);
		JsonNode kyc = applicant.send(HttpMethod.POST, "/kyc-sessions", Map.of("method", "DIGILOCKER"), 201);
		applicant.send(HttpMethod.GET, "/kyc-sessions/" + kyc.get("session_id").asString(), null, 200);
		applicant.submitStep("LIVE_PHOTO", Map.of("document_id", applicant.upload("LIVE_PHOTO")));
		applicant.upload("ADDRESS_PROOF");
		applicant.upload("INCORPORATION_CERTIFICATE");
		applicant.submitStep("DOCUMENTS", Map.of());
		addBank(applicant, "9876501111"); // fake bank returns another holder name
		applicant.submitStep("WHITE_LABEL_SETUP", Map.of("app_name", "Mismatch", "subdomain_label", "staff-mismatch"));
		applicant.submitStep("DECLARATION", Map.of("declaration_version", "2026-10-01", "accepted", true));
		signAgreement(applicant);

		Staff reviewer = staff("9700000001");
		JsonNode view = reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		assertThat(view.get("flags").toString()).contains("BANK_HOLDER_DIFFERS");
		assertThat(view.get("approval_blockers").toString()).contains("BANK_NEEDS_DECISION");
		String bankVerification = null;
		for (JsonNode attempt : view.get("verifications")) {
			if (attempt.get("kind").asString().equals("BANK") && attempt.get("status").asString().equals("MANUAL_REVIEW")) {
				bankVerification = attempt.get("verification_id").asString();
			}
		}
		JsonNode decided = reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/verifications/" + bankVerification
				+ "/decide", Map.of("decision", "ACCEPT", "reason_code", "PROPRIETOR_NAME"), 200);
		assertThat(decided.get("approval_blockers").toString()).doesNotContain("BANK_NEEDS_DECISION");
	}

	@Test
	void correctionReopensStepsAndTheApplicantResubmits() throws Exception {
		Session applicant = submittedApplication("9811120804", "ABCCH2345J", "staff-fix");
		Staff reviewer = staff("9700000001");
		reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/corrections", Map.of("step_keys",
				List.of("BUSINESS_DETAILS"), "reason_code", "ADDRESS_MISMATCH", "message", "Use the address on the GST certificate"),
				200);

		JsonNode snapshot = applicant.snapshot();
		assertThat(snapshot.get("status").asString()).isEqualTo("CORRECTION_REQUIRED");
		assertThat(snapshot.at("/correction/message").asString()).contains("GST certificate");
		assertThat(applicant.stepStatus("BUSINESS_DETAILS")).isEqualTo("REOPENED");
		assertThat(applicant.stepStatus("BUSINESS_PAN")).isEqualTo("LOCKED");

		Map<String, Object> changed = new java.util.LinkedHashMap<>(businessDetails(applicant.upload("SIGNATURE")));
		changed.put("current_address_line1", "Plot 9 Okhla Industrial Area");
		applicant.submitStep("BUSINESS_DETAILS", changed);
		// The declaration and the agreement show these details, so they are done again
		assertThat(applicant.stepStatus("DECLARATION")).isEqualTo("REOPENED");
		assertThat(applicant.stepStatus("AGREEMENT_ESIGN")).isEqualTo("REOPENED");
		applicant.submitStep("DECLARATION", Map.of("declaration_version", "2026-10-01", "accepted", true));
		JsonNode signed = signAgreement(applicant);
		assertThat(signed.get("version").asInt()).isEqualTo(2);
		assertThat(signed.at("/application/status").asString()).isEqualTo("SUBMITTED");
		reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
	}

	@Test
	void casesBelongToTheReviewerWhoClaimedThem() throws Exception {
		Session applicant = submittedApplication("9811120805", "ABCCJ2345K", "staff-mine");
		Staff rita = staff("9700000001");
		Staff ravi = staff("9700000003");
		assertThat(rita.call(HttpMethod.POST, "/applications/" + applicant.id + "/recommendations",
				Map.of("recommendation", "APPROVE"), 409).get("code").asString()).isEqualTo("INVALID_STATE_TRANSITION");
		rita.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		assertThat(ravi.call(HttpMethod.POST, "/applications/" + applicant.id + "/recommendations",
				Map.of("recommendation", "APPROVE"), 403).get("code").asString()).isEqualTo("NOT_ASSIGNED");
		JsonNode mine = rita.call(HttpMethod.GET, "/applications?assigned_to=me&status=UNDER_REVIEW", null, 200);
		assertThat(mine.get("items").findValuesAsString("onboarding_id")).contains(applicant.id);
	}

	@Test
	void holdThenRejectReleasesTheSubdomain() throws Exception {
		Session applicant = submittedApplication("9811120806", "ABCCK2345L", "staff-reject");
		Staff reviewer = staff("9700000001");
		Staff approver = staff("9700000002");
		reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/holds", Map.of("reason_code", "RISK_CHECK"), 200);
		assertThat(applicant.snapshot().get("status").asString()).isEqualTo("ON_HOLD");
		approver.call(HttpMethod.POST, "/applications/" + applicant.id + "/reject",
				Map.of("reason_code", "RISK_FAILED", "applicant_message", "We cannot onboard you"), 200);
		assertThat(applicant.snapshot().get("status").asString()).isEqualTo("REJECTED");
		Session other = login("9811120807");
		assertThat(other.send(HttpMethod.GET, "/v1/onboarding/subdomains/availability?label=staff-reject", null, 200)
			.get("available").asBoolean()).isTrue();
	}

	@Test
	void revealingPersonalDataNeedsThePermissionAndIsAudited() throws Exception {
		Session applicant = submittedApplication("9811120808", "ABCCL2345M", "staff-pii");
		Staff reviewer = staff("9700000001");
		Staff approver = staff("9700000002");
		Staff admin = staff("9700000004");
		reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/pii-reveal",
				Map.of("field", "PAN", "purpose", "check"), 403);
		JsonNode revealed = approver.call(HttpMethod.POST, "/applications/" + applicant.id + "/pii-reveal",
				Map.of("field", "PAN", "purpose", "Match with the PAN card image"), 200);
		assertThat(revealed.get("value").asString()).isEqualTo("ABCCL2345M");
		JsonNode audit = admin.call(HttpMethod.GET, "/applications/" + applicant.id + "/audit", null, 200);
		assertThat(audit.get("items").findValuesAsString("action")).contains("PII_REVEALED", "ONBOARDING_SUBMIT");
		assertThat(audit.toString()).doesNotContain("ABCCL2345M");
		reviewer.call(HttpMethod.GET, "/applications/" + applicant.id + "/audit", null, 403);
	}

	@Test
	void staffAndApplicantTokensStayApartAndGoogleChecksTheDomain() throws Exception {
		Session applicant = login("9811120809");
		Staff reviewer = staff("9700000001");
		assertThat(mvc.get().uri("/v1/admin/me").header("Authorization", "Bearer " + applicant.token).exchange()
			.getResponse().getStatus()).isEqualTo(403);
		assertThat(mvc.get().uri("/v1/onboarding/applications/current").header("Authorization", "Bearer " + reviewer.token)
			.exchange().getResponse().getStatus()).isEqualTo(403);
		assertThat(reviewer.call(HttpMethod.GET, "/me", null, 200).get("permissions").toString()).contains("onboarding.review");

		assertThat(google("test-google:anil.approver@eko.co.in:eko.co.in")).isEqualTo(200);
		assertThat(google("test-google:anil.approver@eko.co.in:gmail.com")).isEqualTo(403);
		assertThat(google("test-google:stranger@eko.co.in:eko.co.in")).isEqualTo(403);
	}

	private int google(String idToken) throws Exception {
		return mvc.post().uri("/v1/admin/auth/google").contentType(MediaType.APPLICATION_JSON)
			.content("{\"id_token\":\"" + idToken + "\"}").exchange().getResponse().getStatus();
	}
}
