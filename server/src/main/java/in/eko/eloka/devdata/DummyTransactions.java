package in.eko.eloka.devdata;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import in.eko.eloka.history.NewTxn;
import in.eko.eloka.history.TxnStatus;
import in.eko.eloka.network.UserView;

/**
 * Generates believable, repeatable dummy transactions for one agent over the last 90 days
 * (local seed only). Same user code → same transactions.
 */
final class DummyTransactions {

	private record Product(int txTypeId, String name, boolean debit, int minAmount, int maxAmount, BigDecimal feeRate,
			BigDecimal commissionRate, int weight) {
	}

	private static final List<Product> PRODUCTS = List.of(
			new Product(221, "Money Transfer", true, 200, 9000, new BigDecimal("0.010"), new BigDecimal("0.004"), 40),
			new Product(225, "AePS Cashout", false, 100, 6000, BigDecimal.ZERO, new BigDecimal("0.003"), 25),
			new Product(244, "Bill Payment", true, 150, 4000, BigDecimal.ZERO, new BigDecimal("0.005"), 15),
			new Product(860, "QR Payment", false, 50, 2500, new BigDecimal("0.004"), new BigDecimal("0.001"), 10),
			new Product(64, "Mobile Recharge", true, 49, 799, BigDecimal.ZERO, new BigDecimal("0.020"), 10));

	private static final List<String> CUSTOMERS = List.of("Anil Kumar", "Sunita Devi", "Mohammed Irfan", "Lakshmi N",
			"Rahul Verma", "Pooja Singh", "Joseph Mathew", "Kavita Rao");

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	private DummyTransactions() {
	}

	static List<NewTxn> forUser(UserView user, Long accountId, Clock clock) {
		Random random = new Random(user.userCode().hashCode());
		LocalDate today = LocalDate.now(clock.withZone(IST));
		LocalDate start = today.minusDays(89).isBefore(user.dateOfJoining()) ? user.dateOfJoining() : today.minusDays(89);
		int totalWeight = PRODUCTS.stream().mapToInt(Product::weight).sum();
		List<NewTxn> txns = new ArrayList<>();
		for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
			int perDay = random.nextInt(5);
			for (int i = 0; i < perDay; i++) {
				Product product = pick(random, totalWeight);
				Instant at = day.atTime(9 + random.nextInt(11), random.nextInt(60)).atZone(IST).toInstant();
				if (at.isAfter(Instant.now(clock))) {
					continue;
				}
				BigDecimal amount = BigDecimal.valueOf(product.minAmount() + random.nextInt(product.maxAmount() - product.minAmount()));
				int roll = random.nextInt(100);
				TxnStatus status = roll < 94 ? TxnStatus.SUCCESS : roll < 97 ? TxnStatus.FAILED : TxnStatus.PENDING;
				BigDecimal fee = amount.multiply(product.feeRate()).setScale(2, RoundingMode.HALF_UP);
				BigDecimal commission = status == TxnStatus.SUCCESS
						? amount.multiply(product.commissionRate()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
				String customer = CUSTOMERS.get(random.nextInt(CUSTOMERS.size()));
				String customerMobile = "98" + String.format("%08d", random.nextInt(100_000_000));
				txns.add(new NewTxn(user.orgId(), user.id(), user.userCode(), accountId, product.txTypeId(), product.name(),
						product.debit(), amount, fee, commission, status, customer, customerMobile, at, Map.of()));
			}
		}
		return txns;
	}

	private static Product pick(Random random, int totalWeight) {
		int r = random.nextInt(totalWeight);
		for (Product p : PRODUCTS) {
			r -= p.weight();
			if (r < 0) {
				return p;
			}
		}
		return PRODUCTS.getLast();
	}
}
