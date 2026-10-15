package in.eko.eloka.gateway.internal.envelope;

/** A file part of a multipart request. */
public record UploadedFile(String field, String filename, String contentType, byte[] bytes) {
}
