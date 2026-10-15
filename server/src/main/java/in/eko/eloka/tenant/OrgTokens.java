package in.eko.eloka.tenant;

import java.util.Optional;

/**
 * Short signed tokens that identify a tenant to public screens (the login page receives one
 * from the org lookup and sends it back with OTP requests). Not a session.
 */
public interface OrgTokens {

	String issue(long orgId);

	/** @return the org id if the token is authentic and not expired */
	Optional<Long> verify(String token);
}
