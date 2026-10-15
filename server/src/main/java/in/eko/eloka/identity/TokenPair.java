package in.eko.eloka.identity;

import java.time.Duration;

/** A fresh access + refresh token. Shown to the client once, stored only as hashes. */
public record TokenPair(String accessToken, String refreshToken, Duration expiresIn) {
}
