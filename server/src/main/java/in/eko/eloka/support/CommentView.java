package in.eko.eloka.support;

import java.time.Instant;

/**
 * One comment.
 *
 * @param authorRole {@code ME} (the viewer), {@code ADMIN}, {@code AGENT} or {@code SUPPORT}
 */
public record CommentView(String authorRole, String text, Instant at) {
}
