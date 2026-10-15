/**
 * Transaction history read model: what the history, statement and dashboard screens show.
 * Records come from imports and the local seed (money movement itself is out of scope,
 * ADR-0004). Customer mobiles are stored encrypted.
 *
 * <p>Public API: {@link in.eko.eloka.history.HistoryApi}.
 */
@ApplicationModule(displayName = "History")
package in.eko.eloka.history;

import org.springframework.modulith.ApplicationModule;
