package in.eko.eloka.reference;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Dropdown reference lists. States use the names the client matches on (ShopCard compares labels). */
@Component
public class ReferenceData {

	private static final List<Option> STATES = List.of("Andaman and Nicobar Islands", "Andhra Pradesh",
			"Arunachal Pradesh", "Assam", "Bihar", "Chandigarh", "Chhattisgarh", "Dadra and Nagar Haveli and Daman and Diu",
			"National Capital Territory of Delhi (UT)", "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir",
			"Jharkhand", "Karnataka", "Kerala", "Ladakh", "Lakshadweep", "Madhya Pradesh", "Maharashtra", "Manipur",
			"Meghalaya", "Mizoram", "Nagaland", "Odisha", "Puducherry", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu",
			"Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal").stream()
		.map(name -> Option.of(name, name))
		.toList();

	private static final List<Option> SHOP_TYPES = List.of(Option.of("1", "Kirana / General Store"),
			Option.of("2", "Mobile / Electronics Shop"), Option.of("3", "Medical Store"), Option.of("4", "Cyber Cafe / CSC"),
			Option.of("5", "Travel Agency"), Option.of("6", "Other"));

	private static final List<Option> BANKS = List.of(bank("SBIN", "State Bank of India", "108"),
			bank("HDFC", "HDFC Bank", "56"), bank("ICIC", "ICICI Bank", "60"), bank("UTIB", "Axis Bank", "14"),
			bank("PUNB", "Punjab National Bank", "100"), bank("BARB", "Bank of Baroda", "16"),
			bank("CNRB", "Canara Bank", "28"), bank("KKBK", "Kotak Mahindra Bank", "69"));

	public List<Option> states() {
		return STATES;
	}

	public List<Option> shopTypes() {
		return SHOP_TYPES;
	}

	public List<Option> banks() {
		return BANKS;
	}

	/**
	 * State for a 6-digit PIN code, from India Post's PIN prefixes (first 3 digits, then first 2).
	 * The city is not derivable without a full PIN directory, so callers ask the user for it.
	 */
	public java.util.Optional<String> stateForPincode(String pincode) {
		if (pincode == null || !pincode.matches("[1-9]\\d{5}")) {
			return java.util.Optional.empty();
		}
		String three = pincode.substring(0, 3);
		String exact = PIN_PREFIX_3.get(three);
		if (exact != null) {
			return java.util.Optional.of(exact);
		}
		return java.util.Optional.ofNullable(PIN_PREFIX_2.get(pincode.substring(0, 2)));
	}

	private static final Map<String, String> PIN_PREFIX_3 = Map.ofEntries(Map.entry("160", "Chandigarh"),
			Map.entry("194", "Ladakh"), Map.entry("246", "Uttarakhand"), Map.entry("247", "Uttarakhand"),
			Map.entry("248", "Uttarakhand"), Map.entry("249", "Uttarakhand"), Map.entry("262", "Uttarakhand"),
			Map.entry("263", "Uttarakhand"), Map.entry("396", "Dadra and Nagar Haveli and Daman and Diu"),
			Map.entry("403", "Goa"), Map.entry("605", "Puducherry"), Map.entry("682", "Kerala"),
			Map.entry("737", "Sikkim"), Map.entry("744", "Andaman and Nicobar Islands"), Map.entry("790", "Arunachal Pradesh"),
			Map.entry("791", "Arunachal Pradesh"), Map.entry("792", "Arunachal Pradesh"), Map.entry("793", "Meghalaya"),
			Map.entry("794", "Meghalaya"), Map.entry("795", "Manipur"), Map.entry("796", "Mizoram"),
			Map.entry("797", "Nagaland"), Map.entry("798", "Nagaland"), Map.entry("799", "Tripura"),
			Map.entry("814", "Jharkhand"), Map.entry("815", "Jharkhand"), Map.entry("816", "Jharkhand"),
			Map.entry("822", "Jharkhand"), Map.entry("825", "Jharkhand"), Map.entry("826", "Jharkhand"),
			Map.entry("827", "Jharkhand"), Map.entry("828", "Jharkhand"), Map.entry("829", "Jharkhand"),
			Map.entry("831", "Jharkhand"), Map.entry("832", "Jharkhand"), Map.entry("833", "Jharkhand"),
			Map.entry("834", "Jharkhand"), Map.entry("835", "Jharkhand"));

	private static final Map<String, String> PIN_PREFIX_2 = Map.ofEntries(
			Map.entry("11", "National Capital Territory of Delhi (UT)"), Map.entry("12", "Haryana"), Map.entry("13", "Haryana"),
			Map.entry("14", "Punjab"), Map.entry("15", "Punjab"), Map.entry("16", "Punjab"), Map.entry("17", "Himachal Pradesh"),
			Map.entry("18", "Jammu and Kashmir"), Map.entry("19", "Jammu and Kashmir"), Map.entry("20", "Uttar Pradesh"),
			Map.entry("21", "Uttar Pradesh"), Map.entry("22", "Uttar Pradesh"), Map.entry("23", "Uttar Pradesh"),
			Map.entry("24", "Uttar Pradesh"), Map.entry("25", "Uttar Pradesh"), Map.entry("26", "Uttar Pradesh"),
			Map.entry("27", "Uttar Pradesh"), Map.entry("28", "Uttar Pradesh"), Map.entry("30", "Rajasthan"),
			Map.entry("31", "Rajasthan"), Map.entry("32", "Rajasthan"), Map.entry("33", "Rajasthan"), Map.entry("34", "Rajasthan"),
			Map.entry("36", "Gujarat"), Map.entry("37", "Gujarat"), Map.entry("38", "Gujarat"), Map.entry("39", "Gujarat"),
			Map.entry("40", "Maharashtra"), Map.entry("41", "Maharashtra"), Map.entry("42", "Maharashtra"),
			Map.entry("43", "Maharashtra"), Map.entry("44", "Maharashtra"), Map.entry("45", "Madhya Pradesh"),
			Map.entry("46", "Madhya Pradesh"), Map.entry("47", "Madhya Pradesh"), Map.entry("48", "Madhya Pradesh"),
			Map.entry("49", "Chhattisgarh"), Map.entry("50", "Telangana"), Map.entry("51", "Andhra Pradesh"),
			Map.entry("52", "Andhra Pradesh"), Map.entry("53", "Andhra Pradesh"), Map.entry("56", "Karnataka"),
			Map.entry("57", "Karnataka"), Map.entry("58", "Karnataka"), Map.entry("59", "Karnataka"),
			Map.entry("60", "Tamil Nadu"), Map.entry("61", "Tamil Nadu"), Map.entry("62", "Tamil Nadu"),
			Map.entry("63", "Tamil Nadu"), Map.entry("64", "Tamil Nadu"), Map.entry("67", "Kerala"), Map.entry("68", "Kerala"),
			Map.entry("69", "Kerala"), Map.entry("70", "West Bengal"), Map.entry("71", "West Bengal"),
			Map.entry("72", "West Bengal"), Map.entry("73", "West Bengal"), Map.entry("74", "West Bengal"),
			Map.entry("75", "Odisha"), Map.entry("76", "Odisha"), Map.entry("77", "Odisha"), Map.entry("78", "Assam"),
			Map.entry("80", "Bihar"), Map.entry("81", "Bihar"), Map.entry("82", "Bihar"), Map.entry("83", "Jharkhand"),
			Map.entry("84", "Bihar"), Map.entry("85", "Bihar"));

	private static Option bank(String code, String name, String bankId) {
		return new Option(code, name, Map.of("bank_id", bankId));
	}
}
