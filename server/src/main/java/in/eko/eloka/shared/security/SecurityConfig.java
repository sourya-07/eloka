package in.eko.eloka.shared.security;

import java.io.IOException;
import java.util.List;
import in.eko.eloka.shared.config.ElokaProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * HTTP security. Authentication is a bearer token resolved by {@link CallerResolver}.
 * <ul>
 * <li>EPS-compatible routes are open at the URL level: the gateway decides per interaction
 * which caller is required (login calls are public, the rest need a session).</li>
 * <li>{@code /v1/onboarding/**} and {@code /v1/admin/**} need a session.</li>
 * <li>Everything else is denied.</li>
 * </ul>
 * Errors are always JSON, because the client's proxy parses every response as JSON.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET) // not in the one-shot migration job
public class SecurityConfig {

	/** Paths of the EPS wire format (see docs/eps-compat/CONTRACT.md). */
	public static final String[] EPS_PATHS = { "/", "/transactions/**", "/authentication/**", "/ekoicici/**",
			"/reports/**" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CallerResolver callerResolver) throws Exception {
		return http
				.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf.disable()) // bearer tokens only, no cookies → no CSRF exposure
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(new BearerTokenFilter(callerResolver), AnonymousAuthenticationFilter.class)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/v1/system/**", "/v1/dev/**", "/error").permitAll()
						.requestMatchers(EPS_PATHS).permitAll()
						.requestMatchers("/v1/onboarding/**", "/v1/admin/**").authenticated()
						.anyRequest().denyAll())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, e) -> writeJson(response,
								HttpServletResponse.SC_UNAUTHORIZED, "Your session has expired. Please log in again."))
						.accessDeniedHandler((request, response, e) -> writeJson(response,
								HttpServletResponse.SC_FORBIDDEN, "You are not allowed to do this.")))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(ElokaProperties properties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOriginPatterns(properties.corsAllowedOriginPatterns());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setExposedHeaders(List.of("X-Request-Id"));
		config.setAllowCredentials(false);
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	private static void writeJson(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"status\":1,\"response_status_id\":1,\"response_type_id\":-1,\"message\":\""
				+ message + "\",\"data\":{}}");
	}
}
