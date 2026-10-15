/**
 * Wallet accounts and their append-only ledger. Balances are a read model: in production
 * nothing moves money yet (transactions are off, ADR-0004); postings come from imports,
 * admin actions or the local seed.
 *
 * <p>Public API: {@link in.eko.eloka.wallet.WalletApi}.
 */
@ApplicationModule(displayName = "Wallet")
package in.eko.eloka.wallet;

import org.springframework.modulith.ApplicationModule;
