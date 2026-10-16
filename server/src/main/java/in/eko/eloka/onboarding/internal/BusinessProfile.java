package in.eko.eloka.onboarding.internal;

import java.util.UUID;
import in.eko.eloka.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Verified business data of one application. PAN, mobiles and email are stored encrypted with a
 * blind index and a masked copy; the Aadhaar number is never stored (only DigiLocker's masked value).
 */
@Entity
@Table(name = "business_profile")
class BusinessProfile extends BaseEntity {

	@Column(name = "application_id", nullable = false, unique = true, columnDefinition = "BINARY(16)")
	private UUID applicationId;

	@Column(name = "legal_name", length = 150)
	private String legalName;

	@Column(name = "company_type", length = 40)
	private String companyType;

	@Column(name = "signatory_name", length = 150)
	private String signatoryName;

	@Column(name = "contact_mobile_enc", columnDefinition = "VARBINARY(128)")
	private byte[] contactMobileEnc;

	@Column(name = "contact_mobile_masked", length = 10)
	private String contactMobileMasked;

	@Column(name = "alternate_mobile_enc", columnDefinition = "VARBINARY(128)")
	private byte[] alternateMobileEnc;

	@Column(name = "address_line1", length = 200)
	private String addressLine1;

	@Column(name = "address_line2", length = 200)
	private String addressLine2;

	@Column(length = 100)
	private String district;

	@Column(length = 100)
	private String state;

	@Column(length = 6, columnDefinition = "CHAR(6)")
	private String pincode;

	@Column(length = 15)
	private String gstin;

	@Column(length = 21)
	private String cin;

	@Column(name = "signature_document_id", columnDefinition = "BINARY(16)")
	private UUID signatureDocumentId;

	@Column(name = "pan_enc", columnDefinition = "VARBINARY(128)")
	private byte[] panEnc;

	@Column(name = "pan_hash", columnDefinition = "BINARY(32)")
	private byte[] panHash;

	@Column(name = "pan_masked", length = 10)
	private String panMasked;

	@Column(name = "pan_holder_type", length = 1, columnDefinition = "CHAR(1)")
	private String panHolderType;

	@Column(name = "name_as_per_pan", length = 150)
	private String nameAsPerPan;

	@Column(name = "email_enc", columnDefinition = "VARBINARY(512)")
	private byte[] emailEnc;

	@Column(name = "email_hash", columnDefinition = "BINARY(32)")
	private byte[] emailHash;

	@Column(name = "email_masked", length = 254)
	private String emailMasked;

	@Column(name = "kyc_name", length = 150)
	private String kycName;

	@Column(name = "aadhaar_masked", length = 14)
	private String aadhaarMasked;

	@Column(name = "photo_document_id", columnDefinition = "BINARY(16)")
	private UUID photoDocumentId;

	@Column(name = "photo_latlong", length = 60)
	private String photoLatlong;

	protected BusinessProfile() {
	}

	BusinessProfile(UUID applicationId) {
		this.applicationId = applicationId;
	}

	void pan(byte[] enc, byte[] hash, String masked, String holderType, String nameAsPerPan) {
		this.panEnc = enc;
		this.panHash = hash;
		this.panMasked = masked;
		this.panHolderType = holderType;
		this.nameAsPerPan = nameAsPerPan;
	}

	void details(BusinessDetailsInput d, byte[] contactEnc, String contactMasked, byte[] alternateEnc) {
		this.legalName = d.name();
		this.companyType = d.companyType();
		this.signatoryName = d.signatoryName();
		this.contactMobileEnc = contactEnc;
		this.contactMobileMasked = contactMasked;
		this.alternateMobileEnc = alternateEnc;
		this.addressLine1 = d.addressLine1();
		this.addressLine2 = d.addressLine2();
		this.district = d.district();
		this.state = d.state();
		this.pincode = d.pincode();
		this.gstin = d.gstin();
		this.cin = d.cin();
		this.signatureDocumentId = d.signatureDocumentId();
	}

	void email(byte[] enc, byte[] hash, String masked) {
		this.emailEnc = enc;
		this.emailHash = hash;
		this.emailMasked = masked;
	}

	void kyc(String name, String aadhaarMaskedValue) {
		this.kycName = name;
		this.aadhaarMasked = aadhaarMaskedValue;
	}

	void livePhoto(UUID documentId, String latlong) {
		this.photoDocumentId = documentId;
		this.photoLatlong = latlong;
	}

	String getLegalName() {
		return legalName;
	}

	String getSignatoryName() {
		return signatoryName;
	}

	String getNameAsPerPan() {
		return nameAsPerPan;
	}

	String getPanMasked() {
		return panMasked;
	}

	byte[] getPanHash() {
		return panHash;
	}

	String getCompanyType() {
		return companyType;
	}

	String getAddressLine1() {
		return addressLine1;
	}

	String getAddressLine2() {
		return addressLine2;
	}

	String getDistrict() {
		return district;
	}

	String getState() {
		return state;
	}

	String getPincode() {
		return pincode;
	}

	String getEmailMasked() {
		return emailMasked;
	}

	String getKycName() {
		return kycName;
	}

	UUID getSignatureDocumentId() {
		return signatureDocumentId;
	}
}
