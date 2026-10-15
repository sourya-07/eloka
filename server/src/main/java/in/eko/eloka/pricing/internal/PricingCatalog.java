package in.eko.eloka.pricing.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import in.eko.eloka.pricing.PricedProduct;
import in.eko.eloka.pricing.Slab;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads {@code pricing/pricing-catalog.json} once and flattens its tree
 * (product → provider → agentPricing / distributorCommission → paymentMode → categoryList)
 * into priced products keyed by productId. Slab limits fall back to the payment mode's and
 * then the form's {@code validation}.
 */
@Component
class PricingCatalog {

	private final String treeJson;
	private final Map<Integer, PricedProduct> products = new LinkedHashMap<>();

	PricingCatalog(JsonMapper json) {
		JsonNode root;
		try (InputStream in = new ClassPathResource("pricing/pricing-catalog.json").getInputStream()) {
			root = json.readTree(in);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read pricing catalog", ex);
		}
		this.treeJson = json.writeValueAsString(root.path("product_list"));
		for (JsonNode product : root.path("product_list")) {
			boolean commission = "commission".equals(product.path("pricingType").asString("pricing"));
			for (JsonNode provider : product.path("provider")) {
				collect(product.path("label").asString(), provider.path("agentPricing"), false, commission);
				collect(product.path("label").asString(), provider.path("distributorCommission"), true, true);
			}
		}
	}

	String treeJson() {
		return treeJson;
	}

	Optional<PricedProduct> product(int productId) {
		return Optional.ofNullable(products.get(productId));
	}

	Collection<PricedProduct> all() {
		return products.values();
	}

	private void collect(String label, JsonNode form, boolean distributor, boolean commission) {
		if (form.isMissingNode() || form.isNull()) {
			return;
		}
		int txType = form.path("txTypeId").asInt(0);
		JsonNode formValidation = form.path("validation");
		add(label, form.path("productId"), form.path("slabs"), formValidation, distributor, commission, txType);
		for (JsonNode mode : form.path("paymentMode")) {
			JsonNode modeValidation = mode.has("validation") ? mode.path("validation") : formValidation;
			JsonNode modeId = mode.has("productId") ? mode.path("productId") : form.path("productId");
			add(label, modeId, mode.has("slabs") ? mode.path("slabs") : form.path("slabs"), modeValidation, distributor,
					commission, txType);
			categories(label, mode.path("categoryList"), modeValidation, distributor, commission, txType);
		}
		categories(label, form.path("categoryList"), formValidation, distributor, commission, txType);
	}

	private void categories(String label, JsonNode list, JsonNode validation, boolean distributor, boolean commission,
			int txType) {
		for (JsonNode category : list) {
			add(label + " - " + category.path("label").asString(), category.path("productId"), category.path("slabs"),
					validation, distributor, commission, txType);
		}
	}

	private void add(String label, JsonNode idNode, JsonNode slabNodes, JsonNode fallback, boolean distributor,
			boolean commission, int txType) {
		if (idNode.isMissingNode() || idNode.isNull() || products.containsKey(idNode.asInt())) {
			return;
		}
		List<Slab> slabs = new ArrayList<>();
		for (JsonNode s : slabNodes) {
			JsonNode v = s.has("validation") ? s.path("validation") : fallback;
			slabs.add(new Slab(s.path("min").decimalValue(), s.path("max").decimalValue(), bound(v.path("percentage"), "min"),
					bound(v.path("percentage"), "max"), bound(v.path("fixed"), "min"), bound(v.path("fixed"), "max")));
		}
		products.put(idNode.asInt(), new PricedProduct(idNode.asInt(), label, distributor, commission, txType, List.copyOf(slabs)));
	}

	/** {@code false}/missing → not allowed (null); an object without the bound → 0 / no upper limit. */
	private static BigDecimal bound(JsonNode limits, String key) {
		if (!limits.isObject()) {
			return null;
		}
		if (limits.has(key)) {
			return limits.path(key).decimalValue();
		}
		return "min".equals(key) ? BigDecimal.ZERO : new BigDecimal("1000000");
	}
}
