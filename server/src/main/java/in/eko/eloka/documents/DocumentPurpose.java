package in.eko.eloka.documents;

/** Why a file was uploaded. Decides who may read it back. */
public enum DocumentPurpose {
	AADHAAR_FRONT,
	AADHAAR_BACK,
	PAN_CARD,
	SELFIE,
	BANK_PROOF,
	SIGNATURE,
	TICKET_ATTACHMENT,
	BULK_ONBOARDING_FILE,
	/** A file of a Local Admin application (P7); read back only by its applicant (and staff in P8). */
	LOCAL_ADMIN_APPLICATION
}
