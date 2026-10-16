package in.eko.eloka.identity;

/**
 * A one-time PinTwin key: the client encodes each PIN digit d as {@code key.charAt(d)} and
 * appends {@code |keyId}, so the PIN never travels in clear.
 */
public record PinKey(long keyId, String key) {
}
