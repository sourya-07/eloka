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

	private static Option bank(String code, String name, String bankId) {
		return new Option(code, name, Map.of("bank_id", bankId));
	}
}
