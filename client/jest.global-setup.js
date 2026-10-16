/**
 * Runs once before all test workers start. Tests format dates in India time, the app's
 * time zone; pin it so results do not depend on the machine (CI runs in UTC).
 */
export default function globalSetup() {
	process.env.TZ = "Asia/Kolkata";
}
