package in.eko.eloka.shared.config;

/** Where the app runs. Drives the production safety checks, never business rules. */
public enum ElokaEnvironment {
	LOCAL,
	TEST,
	STAGING,
	PRODUCTION;

	public boolean isProduction() {
		return this == PRODUCTION;
	}
}
