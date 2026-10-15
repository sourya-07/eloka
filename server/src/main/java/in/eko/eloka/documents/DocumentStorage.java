package in.eko.eloka.documents;

/** Where encrypted file bytes live. Implementations must be idempotent per key. */
public interface DocumentStorage {

	void put(String key, byte[] encryptedBytes);

	byte[] get(String key);
}
