package in.eko.eloka.network;

/** Write side of the network module. */
public interface UserCommands {

	/** @throws in.eko.eloka.shared.error.ConflictException when the mobile already has an account in the org */
	UserView createUser(NewUser user);
}
