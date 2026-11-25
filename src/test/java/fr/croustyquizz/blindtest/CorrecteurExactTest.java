package fr.croustyquizz.blindtest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CorrecteurExactTest {

	private final CorrecteurExact correcteur = new CorrecteurExact();

	@ParameterizedTest
	@CsvSource({
		"halo, Halo",
		"HALO, Halo",
		"beyonce, Beyoncé",
		"'  Crazy   in love ', Crazy in Love",
	})
	void accepteLaReponseSansCasseNiAccentsNiBlancsRepetes(String saisie, String attendu) {
		assertThat(correcteur.estJuste(saisie, attendu)).isTrue();
	}

	@ParameterizedTest
	@CsvSource({
		"la beyonce, Beyoncé",
		"Hallo, Halo",
		"'   ', Halo",
		"'', Halo",
	})
	void refuseUneAutreReponseOuUnChampVide(String saisie, String attendu) {
		assertThat(correcteur.estJuste(saisie, attendu)).isFalse();
	}

	@org.junit.jupiter.api.Test
	void refuseUnChampAbsent() {
		assertThat(correcteur.estJuste(null, "Halo")).isFalse();
	}

}
