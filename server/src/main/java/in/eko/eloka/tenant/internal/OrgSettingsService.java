package in.eko.eloka.tenant.internal;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.tenant.OrgSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

interface OrgSettingRepository extends JpaRepository<OrgSetting, UUID> {

	Optional<OrgSetting> findByOrgIdAndKey(long orgId, String key);
}

@Service
@Transactional(readOnly = true)
class OrgSettingsService implements OrgSettings {

	private final OrgSettingRepository settings;
	private final AuditLog audit;
	private final JsonMapper json;

	OrgSettingsService(OrgSettingRepository settings, AuditLog audit, JsonMapper json) {
		this.settings = settings;
		this.audit = audit;
		this.json = json;
	}

	@Override
	public Optional<String> get(long orgId, String key) {
		return settings.findByOrgIdAndKey(orgId, key).map(OrgSetting::getValue);
	}

	@Override
	@Transactional
	public void put(long orgId, String key, String jsonValue, Caller actor) {
		String value;
		try {
			value = json.writeValueAsString(json.readTree(jsonValue));
		}
		catch (JacksonException ex) {
			throw ValidationException.field(key, "Must be valid JSON");
		}
		settings.findByOrgIdAndKey(orgId, key)
			.ifPresentOrElse(s -> s.replace(value), () -> settings.save(new OrgSetting(orgId, key, value)));
		audit.record(new AuditEntry("ORG_SETTING_CHANGED", "USER", actor.userCode(), orgId, "ORG", Long.toString(orgId),
				Map.of("key", key)));
	}
}
