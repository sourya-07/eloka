package in.eko.eloka.network;

/**
 * Outcome of onboarding one agent. Rows succeed or fail independently.
 *
 * @param user the created user when successful, otherwise null
 * @param reason why it failed, or null
 */
public record OnboardResult(String name, String mobile, UserView user, String reason) {

	public boolean succeeded() {
		return user != null;
	}
}
