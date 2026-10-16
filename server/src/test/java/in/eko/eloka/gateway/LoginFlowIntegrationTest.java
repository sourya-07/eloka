package in.eko.eloka.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import in.eko.eloka.TestcontainersConfiguration;
import in.eko.eloka.network.NewUser;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserCommands;
import in.eko.eloka.network.UserStatus;
import in.eko.eloka.tenant.NewOrg;
import in.eko.eloka.tenant.OrgApi;
import in.eko.eloka.tenant.OrgStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** End-to-end login over the EPS wire format against a real MySQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoginFlowIntegrationTest {

	private static final String RETAILER = "9811100001";
	private static final String ADMIN = "9811100003";
	private static final String INACTIVE = "9811100009";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	OrgApi orgs;

	@Autowired
	UserCommands users;

	private String orgToken;

	@BeforeAll
	void seed() throws Exception {
		long orgId = orgs.createOrg(new NewOrg("Flow Org", "Flow", null, OrgStatus.ACTIVE, List.of("flow.eloka.test"),
				Map.of("theme", "{\"primary\":\"#111111\"}"))).id();
		users.createUser(user(orgId, RETAILER, 2, false, UserStatus.ACTIVE));
		users.createUser(user(orgId, ADMIN, 24, true, UserStatus.ACTIVE));
		users.createUser(user(orgId, INACTIVE, 2, false, UserStatus.INACTIVE));
		orgToken = post("/", Map.of("interaction_type_id", "677", "sub_domain", "flow", "secret", "test-org-meta-secret"))
			.get("org_token").asString();
	}

	@Test
	void orgLookupNeedsSecretAndKnownHost() throws Exception {
		assertThat(post("/", Map.of("interaction_type_id", "677", "sub_domain", "flow", "secret", "wrong")).get("status").asInt())
			.isEqualTo(1);
		assertThat(post("/", Map.of("interaction_type_id", "677", "sub_domain", "nope", "secret", "test-org-meta-secret"))
			.get("response_type_id").asInt()).isEqualTo(1829);
	}

	@Test
	void edgeMayIssueCertificatesOnlyForTenantDomains() {
		assertThat(mvc.get().uri("/v1/system/tls-allowed?domain=flow.eloka.test")).hasStatusOk();
		assertThat(mvc.get().uri("/v1/system/tls-allowed?domain=evil.example.com")).hasStatus(404);
		assertThat(mvc.get().uri("/v1/system/tls-allowed")).hasStatus(404);
	}

	@Test
	void retailerLogsInAndReadsOwnProfile() throws Exception {
		String token = login(RETAILER);
		JsonNode profile = postWithToken("/", Map.of("interaction_type_id", "151", "mobile", RETAILER, "user_code", "99029899"), token);
		assertThat(profile.get("response_type_id").asInt()).isEqualTo(369);
		assertThat(profile.at("/data/user_detail/mobile").asString()).isEqualTo(RETAILER);
		assertThat(profile.at("/data/user_detail/is_org_admin").asInt()).isZero();
	}

	@Test
	void wrongOtpIsFlaggedAndCounted() throws Exception {
		post("/", Map.of("interaction_type_id", "515", "mobile", RETAILER, "org_token", orgToken));
		JsonNode wrong = post("/", Map.of("interaction_type_id", "518", "mobile", RETAILER, "otp", "000000", "org_token", orgToken));
		assertThat(wrong.get("otpFailed").asBoolean()).isTrue();
		assertThat(wrong.get("message").asString()).contains("4 attempts left");
	}

	@Test
	void missingOrgTokenIsRejected() throws Exception {
		JsonNode result = post("/", Map.of("interaction_type_id", "515", "mobile", RETAILER, "org_id", "1"));
		assertThat(result.get("status").asInt()).isEqualTo(1);
		assertThat(result.get("error_code").asString()).isEqualTo("VALIDATION_FAILED");
	}

	@Test
	void inactiveAccountCannotLogIn() throws Exception {
		post("/", Map.of("interaction_type_id", "515", "mobile", INACTIVE, "org_token", orgToken));
		JsonNode result = post("/", Map.of("interaction_type_id", "518", "mobile", INACTIVE, "otp", "123456", "org_token", orgToken));
		assertThat(result.get("accountInactive").asBoolean()).isTrue();
	}

	@Test
	void unknownMobileGetsPreRegistrationSession() throws Exception {
		String token = login("9811100004");
		JsonNode profile = postWithToken("/", Map.of("interaction_type_id", "151", "mobile", "9811100004"), token);
		assertThat(profile.get("response_type_id").asInt()).isEqualTo(370);
	}

	@Test
	void retailerCannotReadOthersOrCallAdminInteractions() throws Exception {
		String token = login(RETAILER);
		assertThat(postWithToken("/", Map.of("interaction_type_id", "151", "mobile", ADMIN), token).get("error_code").asString())
			.isEqualTo("NOT_ALLOWED");
		assertThat(postWithToken("/transactions/dojson", Map.of("interaction_type_id", 774), token).get("error_code").asString())
			.isEqualTo("NOT_ALLOWED");
	}

	@Test
	void sessionCallsWithoutTokenAre401Json() {
		MvcTestResult result = mvc.post().uri("/").contentType(MediaType.APPLICATION_JSON)
			.content("{\"interaction_type_id\":\"151\"}").exchange();
		assertThat(result).hasStatus(401).hasContentType(MediaType.APPLICATION_JSON);
	}

	@Test
	void refreshRotatesAndDetectsReuse() throws Exception {
		post("/", Map.of("interaction_type_id", "515", "mobile", RETAILER, "org_token", orgToken));
		JsonNode verified = post("/", Map.of("interaction_type_id", "518", "mobile", RETAILER, "otp", "123456", "org_token", orgToken));
		String refresh = verified.at("/data/refresh_token").asString();

		JsonNode rotated = post("/authentication/token", Map.of("refresh_token", refresh));
		assertThat(rotated.get("access_token").asString()).startsWith("elk_at_");
		assertThat(rotated.at("/details/mobile").asString()).isEqualTo(RETAILER);

		JsonNode reused = post("/authentication/token", Map.of("refresh_token", refresh));
		assertThat(reused.get("status").asInt()).isEqualTo(1);
		assertThat(mvc.post().uri("/transactions/getip").header("Authorization", "Bearer " + rotated.get("access_token").asString()))
			.hasStatus(401);
	}

	@Test
	void undefinedRefreshTokenIsHttp200Failure() {
		assertThat(mvc.post().uri("/authentication/token").contentType(MediaType.APPLICATION_JSON)
			.content("{\"refresh_token\":\"undefined\"}")).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo(1);
	}

	@Test
	void logoutRevokesTheSession() throws Exception {
		String token = login(RETAILER);
		assertThat(mvc.post().uri("/authentication/revoke").header("Authorization", "Bearer " + token)).hasStatusOk();
		assertThat(mvc.post().uri("/transactions/getip").header("Authorization", "Bearer " + token)).hasStatus(401);
	}

	@Test
	void adminSavesThemeAndOrgLookupReturnsIt() throws Exception {
		String token = login(ADMIN);
		JsonNode saved = postWithToken("/transactions/dojson", Map.of("interaction_type_id", 774, "orgDetails",
				Map.of("metadata", List.of(Map.of("name", "theme", "type", "json", "value", Map.of("primary", "#222222"))))), token);
		assertThat(saved.get("status").asInt()).isZero();
		JsonNode org = post("/", Map.of("interaction_type_id", "677", "sub_domain", "flow", "secret", "test-org-meta-secret"));
		assertThat(org.at("/data/metadata/theme/primary").asString()).isEqualTo("#222222");
	}

	@Test
	void unsupportedInteractionSaysSo() throws Exception {
		assertThat(post("/transactions/do", Map.of("interaction_type_id", 424242)).get("response_type_id").asInt()).isEqualTo(-2);
	}

	private String login(String mobile) throws Exception {
		JsonNode sent = post("/", Map.of("interaction_type_id", "515", "mobile", mobile, "org_token", orgToken));
		assertThat(sent.at("/data/otp_length").asInt()).isEqualTo(6);
		JsonNode verified = post("/", Map.of("interaction_type_id", "518", "mobile", mobile, "otp", "123456", "org_token", orgToken));
		assertThat(verified.get("response_type_id").asInt()).isEqualTo(1541);
		return verified.at("/data/otp_verification_token").asString();
	}

	private JsonNode post(String uri, Map<String, ?> body) throws Exception {
		return postWithToken(uri, body, null);
	}

	private JsonNode postWithToken(String uri, Map<String, ?> body, String token) throws Exception {
		var request = mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
		if (token != null) {
			request = request.header("Authorization", "Bearer " + token);
		}
		MvcTestResult result = request.exchange();
		return json.readTree(result.getResponse().getContentAsString());
	}

	private static NewUser user(long orgId, String mobile, int type, boolean admin, UserStatus status) {
		return new NewUser(orgId, mobile, "Test " + mobile.substring(6), null, type, admin, status, null, null, false,
				ShopView.empty(), null);
	}
}
