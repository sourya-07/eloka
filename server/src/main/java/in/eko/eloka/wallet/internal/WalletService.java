package in.eko.eloka.wallet.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.network.UserCreated;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.wallet.LedgerPosting;
import in.eko.eloka.wallet.WalletAccountView;
import in.eko.eloka.wallet.WalletApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class WalletService implements WalletApi {

	private final WalletAccountRepository accounts;
	private final LedgerEntryRepository ledger;
	private final AuditLog audit;
	private final Clock clock;

	WalletService(WalletAccountRepository accounts, LedgerEntryRepository ledger, AuditLog audit, Clock clock) {
		this.accounts = accounts;
		this.ledger = ledger;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Every new user gets an E-value wallet, in the same transaction as the user itself
	 * (a user without a wallet is never visible).
	 */
	@EventListener
	@Transactional
	void onUserCreated(UserCreated event) {
		ensureEvalueAccount(event.orgId(), event.userId(), event.userCode());
	}

	@Override
	public List<WalletAccountView> accountsOf(long orgId, UUID userId) {
		return accounts.findByOrgIdAndUserIdOrderById(orgId, userId).stream().map(WalletService::toView).toList();
	}

	@Override
	@Transactional
	public WalletAccountView ensureEvalueAccount(long orgId, UUID userId, String userCode) {
		return accounts.findByOrgIdAndUserIdAndTypeId(orgId, userId, WalletAccountView.TYPE_EVALUE)
			.map(WalletService::toView)
			.orElseGet(() -> toView(accounts.save(new WalletAccount(orgId, userId, WalletAccountView.TYPE_EVALUE, "EV" + userCode))));
	}

	@Override
	@Transactional
	public WalletAccountView post(LedgerPosting posting) {
		WalletAccount account = accounts.lockByOwner(posting.orgId(), posting.userId(), WalletAccountView.TYPE_EVALUE)
			.orElseThrow(() -> new NotFoundException("WALLET_NOT_FOUND", "Wallet not found"));
		if (ledger.existsByAccountIdAndReference(account.getId(), posting.reference())) {
			return toView(account);
		}
		Instant now = Instant.now(clock);
		var balanceAfter = account.apply(posting.credit(), posting.amount());
		ledger.save(new LedgerEntry(account.getId(), posting.credit(), posting.amount(), balanceAfter, posting.reference(),
				posting.narration(), now));
		audit.record(new AuditEntry(posting.credit() ? "WALLET_CREDITED" : "WALLET_DEBITED", "SYSTEM", null,
				posting.orgId(), "WALLET", account.getId().toString(),
				Map.of("amount", posting.amount().toPlainString(), "reference", posting.reference())));
		return toView(account);
	}

	@Override
	public in.eko.eloka.wallet.StatementPage statement(long orgId, UUID userId, java.time.LocalDate from,
			java.time.LocalDate to, int offset, int limit) {
		java.time.ZoneId ist = java.time.ZoneId.of("Asia/Kolkata");
		Instant start = from == null ? Instant.EPOCH : from.atStartOfDay(ist).toInstant();
		Instant end = to == null ? Instant.now(clock).plusSeconds(1) : to.plusDays(1).atStartOfDay(ist).toInstant();
		return accounts.findByOrgIdAndUserIdAndTypeId(orgId, userId, WalletAccountView.TYPE_EVALUE)
			.map(account -> {
				var page = ledger.findByAccountIdAndPostedAtGreaterThanEqualAndPostedAtLessThan(account.getId(), start, end,
						new in.eko.eloka.shared.persistence.OffsetPage(Math.max(0, offset), Math.max(1, Math.min(limit, 1000)),
								org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "postedAt")));
				return new in.eko.eloka.wallet.StatementPage(page.getContent().stream().map(LedgerEntry::toLine).toList(),
						page.getTotalElements());
			})
			.orElse(new in.eko.eloka.wallet.StatementPage(List.of(), 0));
	}

	private static WalletAccountView toView(WalletAccount account) {
		return new WalletAccountView(account.getId(), account.getTypeId(), account.getAccountNumber(), account.getBalance());
	}
}
