package in.eko.eloka.gateway.internal.envelope;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

class EpsRequestParserTest {

	private final EpsRequestParser parser = new EpsRequestParser(JsonMapper.builder().build());

	@Test
	void readsJsonEnvelope() throws Exception {
		MockHttpServletRequest request = post("/", "application/json",
				"{\"interaction_type_id\":\"515\",\"mobile\":\"9000000001\",\"user_code\":\"99029899\"}");
		EpsRequest eps = parser.parse(request);
		assertThat(eps.interactionId()).contains("515");
		assertThat(eps.params().string("mobile")).contains("9000000001");
		assertThat(eps.tf()).isEmpty();
	}

	@Test
	void numericInteractionIdIsReadAsText() throws Exception {
		EpsRequest eps = parser.parse(post("/transactions/do", "application/json", "{\"interaction_type_id\":9}"));
		assertThat(eps.interactionId()).contains("9");
	}

	@Test
	void tfHeadersWithQueryInUri() throws Exception {
		MockHttpServletRequest request = post("/transactions/do", "application/json", "{\"filter\":\"x\"}");
		request.addHeader("tf-req-uri-root-path", "/ekoicici/v1");
		request.addHeader("tf-req-uri", "/network/agents?record_count=10&page_number=2");
		request.addHeader("tf-req-method", "get");
		EpsRequest eps = parser.parse(request);
		assertThat(eps.tf()).hasValueSatisfying(tf -> {
			assertThat(tf.root()).isEqualTo("/ekoicici/v1");
			assertThat(tf.method()).isEqualTo(HttpMethod.GET);
			assertThat(tf.uri()).isEqualTo("/network/agents");
		});
		assertThat(eps.params().string("page_number")).contains("2");
		assertThat(eps.params().string("filter")).contains("x");
	}

	@Test
	void proxiedTfCallUsesPathAndQuery() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ekoicici/v1/marketuat/karza/digilocker-redirection-url");
		request.setQueryString("source=WLC&client_ref_id=1");
		EpsRequest eps = parser.parse(request);
		assertThat(eps.tf()).hasValueSatisfying(tf -> {
			assertThat(tf.root()).isEqualTo("/ekoicici/v1/marketuat");
			assertThat(tf.uri()).isEqualTo("/karza/digilocker-redirection-url");
		});
		assertThat(eps.params().string("source")).contains("WLC");
	}

	@Test
	void multipartExpandsFormDataFieldAndKeepsFiles() throws Exception {
		MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
		request.setRequestURI("/");
		request.addParameter("form-data", "interaction_type_id=523&doc_type=2&mobile=9000000004");
		request.addFile(new MockMultipartFile("file1", "pan.png", "image/png", new byte[] { 1, 2, 3 }));
		EpsRequest eps = parser.parse(request);
		assertThat(eps.interactionId()).contains("523");
		assertThat(eps.params().string("doc_type")).contains("2");
		assertThat(eps.files()).singleElement().satisfies(file -> {
			assertThat(file.field()).isEqualTo("file1");
			assertThat(file.bytes()).hasSize(3);
		});
	}

	@Test
	void urlEncodedBodyWorks() throws Exception {
		MockHttpServletRequest request = post("/authentication/token", "application/x-www-form-urlencoded", "");
		request.addParameter("refresh_token", "elk_rt_abc");
		assertThat(parser.parse(request).params().string("refresh_token")).contains("elk_rt_abc");
	}

	@Test
	void undefinedAndBlankAreTreatedAsMissing() throws Exception {
		EpsRequest eps = parser.parse(post("/", "application/json", "{\"a\":\"undefined\",\"b\":\" \",\"c\":null}"));
		assertThat(eps.params().string("a")).isEmpty();
		assertThat(eps.params().string("b")).isEmpty();
		assertThat(eps.params().string("c")).isEmpty();
	}

	private static MockHttpServletRequest post(String uri, String contentType, String body) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
		request.setContentType(contentType);
		request.setContent(body.getBytes(StandardCharsets.UTF_8));
		return request;
	}
}
