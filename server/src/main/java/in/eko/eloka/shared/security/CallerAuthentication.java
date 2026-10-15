package in.eko.eloka.shared.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Spring Security wrapper around a {@link Caller}. */
public final class CallerAuthentication extends AbstractAuthenticationToken {

	private final Caller caller;

	public CallerAuthentication(Caller caller) {
		super(List.of(new SimpleGrantedAuthority("ROLE_" + caller.role().name()),
				new SimpleGrantedAuthority("KIND_" + caller.kind().name())));
		this.caller = caller;
		setAuthenticated(true);
	}

	@Override
	public Object getCredentials() {
		return "";
	}

	@Override
	public Caller getPrincipal() {
		return caller;
	}
}
