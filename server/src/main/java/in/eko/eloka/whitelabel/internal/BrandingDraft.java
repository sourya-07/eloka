package in.eko.eloka.whitelabel.internal;

import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** App name, logo and colours chosen during onboarding; published at activation (P9). */
@Entity
@Table(name = "branding_draft")
class BrandingDraft extends BaseEntity {

	@Column(name = "application_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "app_name", nullable = false, length = 60)
	private String appName;

	@Column(name = "logo_document_id", columnDefinition = "BINARY(16)")
	private UUID logoDocumentId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "JSON")
	private String theme;

	protected BrandingDraft() {
	}

	BrandingDraft(UUID applicationId) {
		this.applicationId = applicationId;
	}

	void update(String newAppName, UUID newLogoDocumentId, String themeJson) {
		this.appName = newAppName;
		this.logoDocumentId = newLogoDocumentId;
		this.theme = themeJson;
	}
}
