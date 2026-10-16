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
		identity.saveStaff("asha.auditor@eko.co.in", "Asha Auditor", "9700000005", EnumSet.of(StaffRole.AUDITOR), true);
		identity.saveStaff("gone.reviewer@eko.co.in", "Gone Reviewer", null, EnumSet.of(StaffRole.REVIEWER), false);
		seeded = true;
	}

	/** A staff session (local/test OTP login). */
	final class Staff {

		final String token;

		Staff(String token) {
			this.token = token;
		}

		/** Actions on a case send If-Match with the case's current ETag, like the console after a reload. */
		JsonNode call(HttpMethod method, String path, Object body, int expected) throws Exception {
			java.util.regex.Matcher action = java.util.regex.Pattern.compile("^/applications/([^/?]+)/(.+)$").matcher(path);
			String ifMatch = method != HttpMethod.GET && action.matches() && !action.group(2).equals("claim")
					&& !action.group(2).equals("pii-reveal") ? etag(action.group(1)) : null;
			return call(method, path, body, ifMatch, expected);
		}

		String etag(String id) throws Exception {
			return mvc.get().uri("/v1/admin/applications/" + id).header("Authorization", "Bearer " + token).exchange()
				.getResponse().getHeader("ETag");
		}

		JsonNode call(HttpMethod method, String path, Object body, String ifMatch, int expected) throws Exception {
			var request = mvc.method(method).uri("/v1/admin" + path)
				.header("Authorization", "Bearer " + token)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body == null ? "{}" : json.writeValueAsString(body));
			if (ifMatch != null) {
				request = request.header("If-Match", ifMatch);
			}
			MvcTestResult result = request.exchange();
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
		return new Staff(staffLogin(mobile).get("access_token").asString());
	}

	/** Staff OTP login; returns the token body. */
	private JsonNode staffLogin(String mobile) throws Exception {
		mvc.post().uri("/v1/admin/auth/otp").contentType(MediaType.APPLICATION_JSON)
			.content("{\"mobile\":\"" + mobile + "\"}").exchange();
		MvcTestResult verified = staffOtpVerify(mobile, "123456");
		assertThat(verified.getResponse().getStatus()).as(verified.getResponse().getContentAsString()).isEqualTo(200);
		return json.readTree(verified.getResponse().getContentAsString());
	}

	private MvcTestResult staffOtpVerify(String mobile, String otp) {
		return mvc.post().uri("/v1/admin/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"mobile\":\"" + mobile + "\",\"otp\":\"" + otp + "\"}").exchange();
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
		for (JsonNode attempt : decided.get("verifications")) {
			if (attempt.get("verification_id").asString().equals(bankVerification)) {
				assertThat(attempt.get("status").asString()).isEqualTo("SUCCEEDED");
				assertThat(attempt.get("failure_code").isNull()).isTrue(); // accepted = no failure code left
			}
		}
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
	void aCorrectionRunsUntilItsDueDateNotTheIdleWindow() throws Exception {
		Session applicant = submittedApplication("9811120810", "ABCCM2345N", "staff-due");
		Staff reviewer = staff("9700000001");
		reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/corrections", Map.of("step_keys",
				List.of("BUSINESS_DETAILS", "SIGNATORY_KYC", "BANK_ACCOUNT"), "reason_code", "ADDRESS_MISMATCH", "message",
				"x".repeat(1001)), 422);
		// Several steps and a long message fit (review_action.target_ref, onboarding_step.reviewer_message)
		reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/corrections", Map.of("step_keys",
				List.of("BUSINESS_DETAILS", "SIGNATORY_KYC", "BANK_ACCOUNT"), "reason_code", "ADDRESS_MISMATCH", "message",
				"x".repeat(1000)), 200);
		JsonNode snapshot = applicant.snapshot();
		assertThat(snapshot.get("expires_at")).isEqualTo(snapshot.at("/correction/due_at"));
		assertThat(reviewer.caseOf(applicant.id).get("review_history").findValuesAsString("target"))
			.contains("BUSINESS_DETAILS,SIGNATORY_KYC,BANK_ACCOUNT");

		// Review took longer than the idle window: the applicant still has until the due date
		jdbc.update("UPDATE onboarding_application SET last_applicant_activity_at = NOW(6) - INTERVAL 31 DAY WHERE public_id = ?",
				applicant.id);
		expiryJob.run();
		assertThat(applicant.snapshot().get("status").asString()).isEqualTo("CORRECTION_REQUIRED");

		jdbc.update("UPDATE onboarding_application SET correction_due_at = NOW(6) - INTERVAL 1 MINUTE WHERE public_id = ?",
				applicant.id);
		expiryJob.run();
		assertThat(applicant.snapshot().get("status").asString()).isEqualTo("EXPIRED");
	}

	@Test
	void aReviewerCanAskForTheAgreementToBeSignedAgain() throws Exception {
		Session applicant = submittedApplication("9811120811", "ABCCN2345P", "staff-resign");
		Staff reviewer = staff("9700000001");
		reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/corrections", Map.of("step_keys",
				List.of("AGREEMENT_ESIGN"), "reason_code", "ESIGN_UNCLEAR", "message", "Please sign the agreement again"), 200);
		applicant.refresh(); // staff changed the application

		JsonNode signed = signAgreement(applicant);
		assertThat(signed.get("version").asInt()).isEqualTo(2);
		assertThat(signed.at("/application/status").asString()).isEqualTo("SUBMITTED");
	}

	@Test
	void anUnchangedCorrectionKeepsTheChecksThatDependOnIt() throws Exception {
		Session applicant = submittedApplication("9811120812", "ABCCP2345Q", "staff-same");
		Staff reviewer = staff("9700000001");
		reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		reviewer.call(HttpMethod.POST, "/applications/" + applicant.id + "/corrections", Map.of("step_keys",
				List.of("BUSINESS_PAN"), "reason_code", "PAN_CARD_UNCLEAR", "message", "Confirm your PAN"), 200);
		applicant.refresh(); // staff changed the application

		applicant.send(HttpMethod.POST, "/pan-verifications", Map.of("pan", "ABCCP2345Q"), 200);
		// Same PAN result: the bank and business checks stand; the declaration is confirmed again
		assertThat(applicant.stepStatus("BUSINESS_DETAILS")).isEqualTo("LOCKED");
		assertThat(applicant.stepStatus("BANK_ACCOUNT")).isEqualTo("LOCKED");
		assertThat(applicant.stepStatus("DECLARATION")).isEqualTo("REOPENED");
		applicant.submitStep("DECLARATION", Map.of("declaration_version", "2026-10-01", "accepted", true));
		JsonNode signed = signAgreement(applicant);
		assertThat(signed.get("version").asInt()).isEqualTo(1); // unchanged agreement, no new signature
		assertThat(signed.at("/application/status").asString()).isEqualTo("SUBMITTED");
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
		// Neither the value nor the free-text purpose reaches the audit log; the purpose stays in the review history
		assertThat(audit.toString()).doesNotContain("ABCCL2345M").doesNotContain("PAN card image");
		assertThat(admin.caseOf(applicant.id).get("review_history").toString()).contains("PAN card image");
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

	@Test
	void anAdminTakesBackACaseFromAnAbsentReviewer() throws Exception {
		Session applicant = submittedApplication("9811120813", "ABCCQ2345R", "staff-away");
		Staff rita = staff("9700000001");
		Staff ravi = staff("9700000003");
		Staff ada = staff("9700000004");
		rita.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		// Another reviewer cannot; an admin must say why
		assertThat(ravi.call(HttpMethod.POST, "/applications/" + applicant.id + "/release", Map.of("reason", "mine now"), 403)
			.get("missing_permission").asString()).isEqualTo("onboarding.reassign");
		ada.call(HttpMethod.POST, "/applications/" + applicant.id + "/release", Map.of(), 422);
		ada.call(HttpMethod.POST, "/applications/" + applicant.id + "/release", Map.of("reason", "Rita is on leave"), 200);
		ravi.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
	}

	@Test
	void whoeverClaimedACaseCannotApproveItLater() throws Exception {
		Session applicant = submittedApplication("9811120814", "ABCCR2345S", "staff-handover");
		Staff ada = staff("9700000004");
		Staff rita = staff("9700000001");
		Staff anil = staff("9700000002");
		ada.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		ada.verifyAllDocuments(applicant.id);
		ada.call(HttpMethod.POST, "/applications/" + applicant.id + "/release", Map.of(), 200);
		rita.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		rita.call(HttpMethod.POST, "/applications/" + applicant.id + "/recommendations", Map.of("recommendation", "APPROVE"),
				200);
		assertThat(ada.call(HttpMethod.POST, "/applications/" + applicant.id + "/approve", null, 403).get("code").asString())
			.isEqualTo("MAKER_CHECKER_VIOLATION");
		anil.call(HttpMethod.POST, "/applications/" + applicant.id + "/approve", null, 200);
	}

	@Test
	void auditorsCannotOpenRawKycFiles() throws Exception {
		Session applicant = submittedApplication("9811120815", "ABCCS2345T", "staff-files");
		Staff asha = staff("9700000005");
		Staff rita = staff("9700000001");
		String document = asha.caseOf(applicant.id).get("documents").get(0).get("document_id").asString();
		String path = "/applications/" + applicant.id + "/documents/" + document + "/download";
		asha.call(HttpMethod.GET, path, null, 403);
		assertThat(mvc.get().uri("/v1/admin" + path).header("Authorization", "Bearer " + rita.token).exchange()
			.getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void staffOtpAnswersTheSameForUnknownNumbers() throws Exception {
		for (String mobile : List.of("9700000003", "9711111111")) {
			assertThat(mvc.post().uri("/v1/admin/auth/otp").contentType(MediaType.APPLICATION_JSON)
				.content("{\"mobile\":\"" + mobile + "\"}").exchange().getResponse().getStatus()).isEqualTo(202);
		}
		MvcTestResult staffWrong = staffOtpVerify("9700000003", "000000");
		MvcTestResult unknownWrong = staffOtpVerify("9711111111", "000000");
		assertThat(unknownWrong.getResponse().getStatus()).isEqualTo(staffWrong.getResponse().getStatus());
		assertThat(read(unknownWrong).get("code")).isEqualTo(read(staffWrong).get("code"));
		assertThat(read(unknownWrong).get("attempts_left")).isEqualTo(read(staffWrong).get("attempts_left"));
	}

	@Test
	void refreshTokensOnlyWorkOnTheirOwnEndpoint() throws Exception {
		String staffRefresh = staffLogin("9700000001").get("refresh_token").asString();
		JsonNode sent = postPublic("/v1/onboarding/otp", Map.of("mobile", "9811120816"), UUID.randomUUID().toString());
		JsonNode applicantTokens = read(mvc.post().uri("/v1/onboarding/otp/verify").contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(Map.of("otp_ref", sent.get("otp_ref").asString(), "otp", "123456"))).exchange());
		String applicantRefresh = applicantTokens.get("refresh_token").asString();

		assertThat(refresh("/v1/admin/auth/refresh", applicantRefresh)).isEqualTo(401);
		assertThat(refresh("/v1/onboarding/session/refresh", staffRefresh)).isEqualTo(401);
		assertThat(refresh("/v1/admin/auth/refresh", staffRefresh)).isEqualTo(200);
	}

	@Test
	void refusedGoogleSignInsAreAudited() throws Exception {
		assertThat(google("test-google:gone.reviewer@eko.co.in:eko.co.in")).isEqualTo(403);
		String staffId = jdbc.queryForObject("SELECT LOWER(BIN_TO_UUID(id)) FROM staff_user WHERE email = ?", String.class,
				"gone.reviewer@eko.co.in");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action = 'STAFF_LOGIN_REFUSED' AND target_id = ?"
				+ " AND details LIKE '%ACCOUNT_DISABLED%'", Integer.class, staffId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE details LIKE '%gone.reviewer%'", Integer.class))
			.isZero();
	}

	@Test
	void reviewInputsThatDoNotFitAreRefusedCleanly() throws Exception {
		Session applicant = submittedApplication("9811120817", "ABCCT2345U", "staff-inputs");
		Staff rita = staff("9700000001");
		rita.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		String corrections = "/applications/" + applicant.id + "/corrections";
		assertThat(rita.call(HttpMethod.POST, corrections, Map.of("step_keys", List.of("BUSINESS_DETAILS"), "reason_code",
				"R".repeat(41), "message", "fix"), 422).toString()).contains("reason_code");
		rita.call(HttpMethod.POST, corrections, Map.of("step_keys", List.of("BUSINESS_DETAILS"), "reason_code", "ADDRESS",
				"message", "fix", "due_in_days", 5_000_000), 422);
		rita.call(HttpMethod.POST, corrections, Map.of("step_keys", List.of("BUSINESS_DETAILS"), "reason_code", "ADDRESS",
				"message", "fix", "due_in_days", 0), 422);
		jdbc.update("UPDATE onboarding_step s JOIN onboarding_application a ON a.id = s.application_id"
				+ " SET s.status = 'NOT_APPLICABLE' WHERE a.public_id = ? AND s.step_key = 'LIVE_PHOTO'", applicant.id);
		assertThat(rita.call(HttpMethod.POST, corrections, Map.of("step_keys", List.of("LIVE_PHOTO"), "reason_code", "PHOTO",
				"message", "Retake the photo"), 422).toString()).contains("step_keys");
		assertThat(rita.caseOf(applicant.id).at("/application/status").asString()).isEqualTo("UNDER_REVIEW");
	}

	@Test
	void aRejectionKeepsTheApplicantMessageAndTheInternalNote() throws Exception {
		Session applicant = submittedApplication("9811120818", "ABCCU2345V", "staff-notes");
		Staff rita = staff("9700000001");
		Staff anil = staff("9700000002");
		rita.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		JsonNode rejected = anil.call(HttpMethod.POST, "/applications/" + applicant.id + "/reject", Map.of("reason_code",
				"PAN_MISMATCH", "applicant_message", "Your PAN does not match", "internal_note", "Suspected fraud, ticket 123"),
				200);
		JsonNode reject = null;
		for (JsonNode item : rejected.get("review_history")) {
			if ("REJECT".equals(item.get("action").asString())) {
				reject = item;
			}
		}
		assertThat(reject.get("applicant_message").asString()).isEqualTo("Your PAN does not match");
		assertThat(reject.get("note").asString()).isEqualTo("Suspected fraud, ticket 123");
	}

	@Test
	void staffActOnlyOnTheVersionOfTheCaseTheySaw() throws Exception {
		Session applicant = submittedApplication("9811120819", "ABCCV2345W", "staff-stale");
		Staff rita = staff("9700000001");
		Staff anil = staff("9700000002");
		rita.call(HttpMethod.POST, "/applications/" + applicant.id + "/claim", null, 200);
		String seen = rita.etag(applicant.id);
		anil.call(HttpMethod.POST, "/applications/" + applicant.id + "/holds", Map.of("reason_code", "RISK_CHECK"), 200);
		anil.call(HttpMethod.DELETE, "/applications/" + applicant.id + "/holds", null, 200);
		// The hold came and went while Rita's screen was open: her recommendation must be refused
		String path = "/applications/" + applicant.id + "/recommendations";
		assertThat(rita.call(HttpMethod.POST, path, Map.of("recommendation", "APPROVE"), seen, 412).get("code").asString())
			.isEqualTo("PRECONDITION_FAILED");
		assertThat(rita.call(HttpMethod.POST, path, Map.of("recommendation", "APPROVE"), null, 428).get("code").asString())
			.isEqualTo("PRECONDITION_REQUIRED");
		rita.call(HttpMethod.POST, path, Map.of("recommendation", "APPROVE"), 200);
	}

	@Test
	void disablingAStaffAccountKeepsItsNameAndRoles() throws Exception {
		identity.saveStaff("leaving.reviewer@eko.co.in", "Leela Reviewer", null, EnumSet.of(StaffRole.REVIEWER), true);
		// What the runbook's ELOKA_STAFF_ACTIVE=false run sends: no name, no roles
		var disabled = identity.saveStaff("leaving.reviewer@eko.co.in", null, null, EnumSet.noneOf(StaffRole.class), false);
		assertThat(disabled.active()).isFalse();
		assertThat(disabled.displayName()).isEqualTo("Leela Reviewer");
		assertThat(disabled.roles()).containsExactly(StaffRole.REVIEWER);
	}

	private int refresh(String uri, String refreshToken) throws Exception {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(Map.of("refresh_token", refreshToken))).exchange().getResponse().getStatus();
	}

	private int google(String idToken) throws Exception {
		return mvc.post().uri("/v1/admin/auth/google").contentType(MediaType.APPLICATION_JSON)
			.content("{\"id_token\":\"" + idToken + "\"}").exchange().getResponse().getStatus();
	}
}
