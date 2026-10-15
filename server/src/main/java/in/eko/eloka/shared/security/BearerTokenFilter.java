package in.eko.eloka.shared.security;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Optional authentication: if a valid bearer token is present, the request runs as that
 * {@link Caller}. Whether a caller is <em>required</em> is decided per route/interaction.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

	private final CallerResolver resolver;

	public BearerTokenFilter(CallerResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
			String token = header.substring(7).trim();
			if (!token.isEmpty() && !"undefined".equals(token) && !"null".equals(token)) {
				resolver.resolve(token).ifPresent(caller -> {
					SecurityContextHolder.getContext().setAuthentication(new CallerAuthentication(caller));
					MDC.put("org_id", String.valueOf(caller.orgId()));
					if (caller.userCode() != null) {
						MDC.put("user_code", caller.userCode());
					}
					MDC.put("session_ref", caller.sessionRef());
				});
			}
		}
		chain.doFilter(request, response);
	}
}
