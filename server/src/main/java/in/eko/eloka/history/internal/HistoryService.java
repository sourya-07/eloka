package in.eko.eloka.history.internal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import in.eko.eloka.history.DashboardStats;
import in.eko.eloka.history.EarningsSummary;
import in.eko.eloka.history.HistoryApi;
import in.eko.eloka.history.NewTxn;
import in.eko.eloka.history.TxnStatus;
import in.eko.eloka.history.TxnView;
import in.eko.eloka.shared.crypto.FieldCipher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
class HistoryService implements HistoryApi {

	/** Business days and months are Indian time. */
	static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	private final TxnRecordRepository records;
	private final TidAllocator tids;
	private final FieldCipher cipher;
	private final JsonMapper json;
	private final Clock clock;

	HistoryService(TxnRecordRepository records, TidAllocator tids, FieldCipher cipher, JsonMapper json, Clock clock) {
		this.records = records;
		this.tids = tids;
		this.cipher = cipher;
		this.json = json;
		this.clock = clock;
	}

	@Override
	@Transactional
	public TxnView record(NewTxn txn) {
		TxnRecord record = records.save(new TxnRecord(tids.next(txn.occurredAt().getEpochSecond()), txn.orgId(), txn.userId(),
				txn.userCode(), txn.accountId(), txn.txTypeId(), txn.txName(), txn.debit(), txn.amount(),
				orZero(txn.fee()), orZero(txn.commission()), txn.status(), txn.customerName(),
				cipher.encrypt(txn.customerMobile()), txn.occurredAt(),
				json.writeValueAsString(txn.details() == null ? Map.of() : new TreeMap<>(txn.details()))));
		return toView(record);
	}

	@Override
	public List<TxnView> recentFor(long orgId, UUID userId, int offset, int limit) {
		int size = Math.max(1, Math.min(limit, 100));
		return records.findByOrgIdAndUserIdOrderByOccurredAtDesc(orgId, userId, PageRequest.of(Math.max(0, offset) / size, size))
			.stream()
			.map(this::toView)
			.toList();
	}

	@Override
	public EarningsSummary earnings(long orgId, UUID userId) {
		LocalDate today = LocalDate.now(clock.withZone(IST));
		LocalDate monthStart = today.withDayOfMonth(1);
		LocalDate lastMonthStart = monthStart.minusMonths(1);
		LocalDate prevMonthStart = monthStart.minusMonths(2);
		BigDecimal thisMonth = sum(orgId, userId, monthStart, today); // till yesterday, as the screen says
		BigDecimal lastMonth = sum(orgId, userId, lastMonthStart, monthStart);
		BigDecimal prevMonth = sum(orgId, userId, prevMonthStart, lastMonthStart);
		return new EarningsSummary(thisMonth, lastMonth, prevMonth, BigDecimal.ZERO.setScale(2));
	}

	@Override
	public DashboardStats dashboard(long orgId, java.util.Collection<UUID> userIds, java.time.Instant from,
			java.time.Instant to, Integer txTypeId) {
		boolean all = userIds == null;
		java.util.Collection<UUID> ids = all || userIds.isEmpty() ? List.of(new UUID(0, 0)) : userIds;

		BigDecimal gtv = BigDecimal.ZERO;
		BigDecimal revenue = BigDecimal.ZERO;
		long total = 0;
		long success = 0;
		long failed = 0;
		long pending = 0;
		Map<Integer, long[]> counts = new TreeMap<>();
		Map<Integer, BigDecimal[]> sums = new TreeMap<>();
		for (Object[] row : records.statsByTypeAndStatus(orgId, all, ids, from, to, txTypeId)) {
			int type = (Integer) row[0];
			TxnStatus status = (TxnStatus) row[1];
			long n = (Long) row[2];
			BigDecimal amount = (BigDecimal) row[3];
			BigDecimal fee = (BigDecimal) row[4];
			total += n;
			long[] c = counts.computeIfAbsent(type, t -> new long[2]);
			BigDecimal[] m = sums.computeIfAbsent(type, t -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
			c[0] += n;
			switch (status) {
				case SUCCESS -> {
					success += n;
					gtv = gtv.add(amount);
					revenue = revenue.add(fee);
					c[1] += n;
					m[0] = m[0].add(amount);
					m[1] = m[1].add(fee);
				}
				case FAILED -> failed += n;
				case PENDING -> pending += n;
				case REFUNDED -> {
				}
			}
		}
		Map<Integer, DashboardStats.TypeStats> byType = new TreeMap<>();
		counts.forEach((type, c) -> byType.put(type,
				new DashboardStats.TypeStats(c[0], c[1], sums.get(type)[0], sums.get(type)[1])));

		Map<UUID, Object[]> perUser = new java.util.LinkedHashMap<>();
		for (Object[] row : records.statsByUserAndStatus(orgId, all, ids, from, to, txTypeId)) {
			Object[] acc = perUser.computeIfAbsent((UUID) row[0],
					id -> new Object[] { row[1], BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L });
			long n = (Long) row[3];
			acc[3] = (Long) acc[3] + n;
			if (row[2] == TxnStatus.SUCCESS) {
				acc[1] = ((BigDecimal) acc[1]).add((BigDecimal) row[4]);
				acc[2] = ((BigDecimal) acc[2]).add((BigDecimal) row[5]);
			}
			if (row[2] == TxnStatus.PENDING) {
				acc[4] = (Long) acc[4] + n;
			}
		}
		List<DashboardStats.UserStats> users = perUser.entrySet().stream()
			.map(e -> new DashboardStats.UserStats(e.getKey(), (String) e.getValue()[0], (BigDecimal) e.getValue()[1],
					(BigDecimal) e.getValue()[2], (Long) e.getValue()[3], (Long) e.getValue()[4]))
			.sorted(java.util.Comparator.comparing(DashboardStats.UserStats::gtv).reversed())
			.limit(50)
			.toList();
		long activeAgents = users.stream().filter(u -> u.gtv().signum() > 0).count();
		return new DashboardStats(gtv, revenue, total, success, failed, pending, activeAgents, byType, users);
	}

	private BigDecimal sum(long orgId, UUID userId, LocalDate from, LocalDate to) {
		return records.sumCommission(orgId, userId, from.atStartOfDay(IST).toInstant(), to.atStartOfDay(IST).toInstant())
			.setScale(2);
	}

	private TxnView toView(TxnRecord r) {
		Map<String, String> details = r.getDetails() == null ? Map.of()
				: json.readValue(r.getDetails(), new TypeReference<Map<String, String>>() {
				});
		return new TxnView(r.getTid(), r.getOrgId(), r.getUserId(), r.getUserCode(), r.getAccountId(), r.getTxTypeId(),
				r.getTxName(), r.isDebit(), r.getAmount(), r.getFee(), r.getCommission(), r.getStatus(),
				r.getCustomerName(), cipher.decrypt(r.getCustomerMobileEnc()), r.getOccurredAt(), details);
	}

	private static BigDecimal orZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}
}
