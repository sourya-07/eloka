package in.eko.eloka.documents;

/** A stored file with its decrypted bytes. */
public record DocumentContent(DocumentRef ref, byte[] bytes) {
}
