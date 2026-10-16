package in.eko.eloka.verification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameMatchTest {

	@Test
	void ignoresCaseAndLegalFormWords() {
		assertThat(NameMatch.score("Shlok Enterprise Pvt. Ltd.", "SHLOK ENTERPRISE PRIVATE LIMITED")).isEqualTo(100);
		assertThat(NameMatch.matches("M/s Shlok Enterprise", "Shlok Enterprise")).isTrue();
	}

	@Test
	void differentNamesDoNotMatch() {
		assertThat(NameMatch.matches("Shlok Enterprise", "Someone Else Traders")).isFalse();
		assertThat(NameMatch.score("Shlok Goswami", "Shlok Kumar Goswami")).isBetween(60, 79);
		assertThat(NameMatch.score(null, "Anything")).isZero();
	}
}
