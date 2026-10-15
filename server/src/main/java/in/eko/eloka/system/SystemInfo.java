package in.eko.eloka.system;

import java.time.Instant;
import java.util.List;

/** Response body of {@code GET /v1/system/info}. */
public record SystemInfo(String name, String version, List<String> profiles, Instant serverTime) {
}
