package in.eko.eloka.system;

import static org.assertj.core.api.Assertions.assertThat;

import in.eko.eloka.shared.config.ElokaProperties;
import in.eko.eloka.shared.security.CallerResolver;
import in.eko.eloka.shared.security.SecurityConfig;
import in.eko.eloka.shared.time.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(SystemInfoController.class)
@Import({SystemInfoService.class, SecurityConfig.class, ClockConfig.class})
@EnableConfigurationProperties(ElokaProperties.class)
class SystemInfoControllerTest {

	@MockitoBean
	CallerResolver callerResolver;

	@Autowired
	MockMvcTester mvc;

	@Test
	void infoIsPublicAndReturnsTheServiceName() {
		assertThat(mvc.get().uri("/v1/system/info"))
				.hasStatusOk()
				.bodyJson()
				.extractingPath("$.name")
				.isEqualTo("eloka-server");
	}

	@Test
	void unknownRoutesAreDeniedByDefault() {
		assertThat(mvc.get().uri("/v1/anything-else")).hasStatus(HttpStatus.UNAUTHORIZED);
	}
}
