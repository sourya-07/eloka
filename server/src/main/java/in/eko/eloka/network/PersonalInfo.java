package in.eko.eloka.network;

import java.time.LocalDate;

/** Personal details shown on the profile. All fields may be null. */
public record PersonalInfo(LocalDate dob, String gender, String maritalStatus, String qualification) {

	public static PersonalInfo empty() {
		return new PersonalInfo(null, null, null, null);
	}
}
