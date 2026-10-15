package in.eko.eloka.tenant.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.tenant.NewOrg;
import in.eko.eloka.tenant.OrgApi;
import in.eko.eloka.tenant.OrgView;
import in.eko.eloka.tenant.TenantProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
class OrgService implements OrgApi {

	private final OrgRepository orgs;
	private final OrgDomainRepository domains;
	private final OrgMetadataRepository metadata;
	private final TenantProperties properties;
	private final AuditLog audit;
	private final JsonMapper json;

	OrgService(OrgRepository orgs, OrgDomainRepository domains, OrgMetadataRepository metadata,
			TenantProperties properties, AuditLog audit, JsonMapper json) {
		this.orgs = orgs;
		this.domains = domains;
		this.metadata = metadata;
		this.properties = properties;
		this.audit = audit;
		this.json = json;
	}

	@Override
	public Optional<OrgView> findByHostname(String hostname) {
		if (hostname == null || hostname.isBlank()) {
			return Optional.empty();
		}
		return domains.findByHostnameAndActiveTrue(hostname.trim().toLowerCase())
			.flatMap(domain -> orgs.findById(domain.getOrgId()))
			.map(this::toView);
	}

	@Override
	public OrgView getById(long orgId) {
		return orgs.findById(orgId).map(this::toView)
			.orElseThrow(() -> new NotFoundException("ORG_NOT_FOUND", "Organisation not found"));
	}

	@Override
	public String hostnameForSubdomain(String label) {
		return label.trim().toLowerCase() + "." + properties.rootDomain();
	}

	@Override
	@Transactional
	public OrgView createOrg(NewOrg request) {
		Org org = orgs.save(new Org(request.name(), request.appName(), request.logoUrl(), request.status()));
		request.hostnames().forEach(host -> domains.save(new OrgDomain(org.getId(), host)));
		request.metadata().forEach((key, value) -> metadata.save(new OrgMetadataEntry(org.getId(), key, requireJson(key, value))));
		audit.record(new AuditEntry("ORG_CREATED", "SYSTEM", null, org.getId(), "ORG", org.getId().toString(),
				Map.of("hostnames", String.valueOf(request.hostnames().size()))));
		return toView(org);
	}

	@Override
	@Transactional
	public void saveMetadata(long orgId, String key, String jsonValue, Caller actor) {
		getById(orgId);
		String value = requireJson(key, jsonValue);
		metadata.findByOrgIdAndKey(orgId, key)
			.ifPresentOrElse(entry -> entry.replaceValue(value), () -> metadata.save(new OrgMetadataEntry(orgId, key, value)));
		audit.record(new AuditEntry("ORG_METADATA_UPDATED", "USER", actor.userCode(), orgId, "ORG",
				Long.toString(orgId), Map.of("key", key)));
	}

	private String requireJson(String key, String value) {
		try {
			return json.writeValueAsString(json.readTree(value));
		}
		catch (JacksonException ex) {
			throw ValidationException.field(key, "Must be valid JSON");
		}
	}

	private OrgView toView(Org org) {
		Map<String, String> entries = new LinkedHashMap<>();
		metadata.findByOrgId(org.getId()).forEach(entry -> entries.put(entry.getKey(), entry.getValue()));
		return new OrgView(org.getId(), org.getName(), org.getAppName(), org.getLogoUrl(), org.getStatus(), entries);
	}
}
