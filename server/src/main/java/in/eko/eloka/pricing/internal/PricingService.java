package in.eko.eloka.pricing.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.network.NetworkQueries;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.network.UserTypes;
import in.eko.eloka.network.UserView;
import in.eko.eloka.pricing.CommissionLine;
import in.eko.eloka.pricing.PricedProduct;
import in.eko.eloka.pricing.PricingApi;
import in.eko.eloka.pricing.PricingChange;
import in.eko.eloka.pricing.PricingRuleView;
import in.eko.eloka.pricing.PricingScope;
import in.eko.eloka.pricing.PricingValue;
import in.eko.eloka.pricing.Slab;
import in.eko.eloka.shared.error.ForbiddenException;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

	List<PricingRule> findByOrgIdAndActiveTrue(long orgId);

	List<PricingRule> findByOrgIdAndProductIdAndScopeAndActiveTrue(long orgId, int productId, PricingScope scope);
}

@Service
@Transactional(readOnly = true)
class PricingService implements PricingApi {

	private static final BigDecimal NO_SLAB = BigDecimal.ZERO;

	private final PricingCatalog catalog;
	private final PricingRuleRepository rules;
	private final NetworkQueries network;
	private final UserDirectory users;
	private final AuditLog audit;

	PricingService(PricingCatalog catalog, PricingRuleRepository rules, NetworkQueries network, UserDirectory users,
			AuditLog audit) {
		this.catalog = catalog;
		this.rules = rules;
		this.network = network;
		this.users = users;
		this.audit = audit;
	}

	@Override
	public String productTreeJson() {
		return catalog.treeJson();
	}

	@Override
	@Transactional
	public void set(Caller caller, PricingChange change) {
		requireAdmin(caller);
		PricedProduct product = product(change.productId());
		Slab slab = slab(product, change.slabMin(), change.slabMax());
		validate(slab, change.percent(), change.value());
		BigDecimal min = slab == null ? NO_SLAB : slab.min();
		BigDecimal max = slab == null ? NO_SLAB : slab.max();

		List<UserView> targets = targets(caller, change.scope(), change.targetUserCodes(), product);
		List<UserView> each = targets.isEmpty() ? java.util.Collections.singletonList(null) : targets;
		for (UserView target : each) {
			UUID targetId = target == null ? null : target.id();
			rules.findByOrgIdAndProductIdAndScopeAndActiveTrue(caller.orgId(), product.productId(), change.scope()).stream()
				.filter(r -> java.util.Objects.equals(r.getTargetUserId(), targetId))
				.filter(r -> r.getSlabMin().compareTo(min) == 0 && r.getSlabMax().compareTo(max) == 0)
				.forEach(PricingRule::supersede);
			rules.save(new PricingRule(caller.orgId(), product.productId(), change.scope(), targetId,
					target == null ? null : target.userCode(), change.percent(), change.value(), min, max, caller.userCode()));
		}
		audit.record(new AuditEntry("PRICING_SET", "USER", caller.userCode(), caller.orgId(), "PRODUCT",
				Integer.toString(product.productId()), Map.of("scope", change.scope().name(), "targets",
						Integer.toString(targets.size()), "value", change.value().toPlainString(),
						"type", change.percent() ? "percent" : "fixed")));
	}

	@Override
	public Optional<PricingValue> current(Caller caller, int productId, PricingScope scope, List<String> targetUserCodes,
			BigDecimal slabMin, BigDecimal slabMax) {
		requireAdmin(caller);
		PricedProduct product = product(productId);
		Slab slab = slab(product, slabMin, slabMax);
		BigDecimal min = slab == null ? NO_SLAB : slab.min();
		BigDecimal max = slab == null ? NO_SLAB : slab.max();
		UUID target = targetUserCodes.isEmpty() ? null : network.findInScope(caller, targetUserCodes.getFirst()).id();
		return active(caller.orgId(), productId, scope, target, min, max).map(PricingRule::toValue);
	}

	@Override
	public List<PricingRuleView> activeRules(Caller caller) {
		requireAdmin(caller);
		return rules.findByOrgIdAndActiveTrue(caller.orgId()).stream().map(r -> {
			PricedProduct p = catalog.product(r.getProductId()).orElse(null);
			return new PricingRuleView(r.getProductId(), p == null ? "Product " + r.getProductId() : p.label(),
					p != null && p.distributorCommission(), r.getScope(), r.getTargetUserCode(), r.toValue(),
					r.getSlabMin(), r.getSlabMax(), r.getCreatedAt());
		}).toList();
	}

	@Override
	public List<CommissionLine> commissionsFor(Caller caller) {
		UserView me = users.getById(caller.orgId(), caller.userId());
		boolean distributor = caller.role() == Role.DISTRIBUTOR;
		UUID parent = me.parentUserId();
		List<CommissionLine> lines = new ArrayList<>();
		for (PricedProduct product : catalog.all()) {
			if (product.distributorCommission() != distributor || product.txTypeId() <= 0) {
				continue;
			}
			List<Slab> slabs = product.slabs().isEmpty()
					? List.of(new Slab(NO_SLAB, NO_SLAB, null, null, null, null)) : product.slabs();
			for (Slab slab : slabs) {
				Optional<PricingRule> rule = active(caller.orgId(), product.productId(), PricingScope.USER, me.id(), slab.min(), slab.max())
					.or(() -> parent == null ? Optional.empty()
							: active(caller.orgId(), product.productId(), PricingScope.NETWORK, parent, slab.min(), slab.max()))
					.or(() -> active(caller.orgId(), product.productId(), PricingScope.ALL, null, slab.min(), slab.max()));
				rule.ifPresent(r -> lines.add(new CommissionLine(product.productId(), product.txTypeId(), product.label(),
						slab.min(), slab.max(), r.toValue())));
			}
		}
		return lines;
	}

	private Optional<PricingRule> active(long orgId, int productId, PricingScope scope, UUID target, BigDecimal min,
			BigDecimal max) {
		return rules.findByOrgIdAndProductIdAndScopeAndActiveTrue(orgId, productId, scope).stream()
			.filter(r -> java.util.Objects.equals(r.getTargetUserId(), target))
			.filter(r -> r.getSlabMin().compareTo(min) == 0 && r.getSlabMax().compareTo(max) == 0)
			.findFirst();
	}

	private List<UserView> targets(Caller caller, PricingScope scope, List<String> codes, PricedProduct product) {
		if (scope == PricingScope.ALL) {
			return List.of();
		}
		if (codes.isEmpty()) {
			throw ValidationException.field("CspList", "Choose at least one user");
		}
		List<UserView> targets = codes.stream().map(code -> network.findInScope(caller, code)).toList();
		boolean needsDistributors = scope == PricingScope.NETWORK || product.distributorCommission();
		if (needsDistributors && targets.stream().anyMatch(u -> u.userType() != UserTypes.DISTRIBUTOR
				&& u.userType() != UserTypes.SUPER_DISTRIBUTOR)) {
			throw ValidationException.field("CspList", "Choose distributors only");
		}
		return targets;
	}

	private PricedProduct product(int productId) {
		return catalog.product(productId)
			.orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "This product cannot be priced"));
	}

	private static Slab slab(PricedProduct product, BigDecimal min, BigDecimal max) {
		if (product.slabs().isEmpty()) {
			return null;
		}
		if (min == null || max == null) {
			throw ValidationException.field("select", "Choose a transaction amount slab");
		}
		return product.slabs().stream().filter(s -> s.matches(min, max)).findFirst()
			.orElseThrow(() -> ValidationException.field("select", "Unknown slab"));
	}

	private static void validate(Slab slab, boolean percent, BigDecimal value) {
		if (value == null || value.signum() < 0) {
			throw ValidationException.field("actual_pricing", "Enter a value of 0 or more");
		}
		if (slab == null) {
			return;
		}
		BigDecimal lo = percent ? slab.percentMin() : slab.fixedMin();
		BigDecimal hi = percent ? slab.percentMax() : slab.fixedMax();
		if (lo == null && hi == null) {
			throw ValidationException.field("pricing_type", percent ? "Percentage is not allowed here" : "A fixed amount is not allowed here");
		}
		if ((lo != null && value.compareTo(lo) < 0) || (hi != null && value.compareTo(hi) > 0)) {
			String from = lo == null ? "0" : lo.stripTrailingZeros().toPlainString();
			String to = hi == null ? "any amount" : hi.stripTrailingZeros().toPlainString();
			throw ValidationException.field("actual_pricing", "Value must be between " + from + " and " + to);
		}
	}

	private static void requireAdmin(Caller caller) {
		if (caller.role() != Role.ORG_ADMIN) {
			throw new ForbiddenException("NOT_ALLOWED", "Only an admin can manage pricing.");
		}
	}
}
