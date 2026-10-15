package in.eko.eloka.network;

import java.util.UUID;

/** Published after a user is created (other modules react, e.g. the wallet opens an account). */
public record UserCreated(UUID userId, long orgId, String userCode, int userType) {
}
