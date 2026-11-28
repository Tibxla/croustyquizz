package fr.croustyquizz.campagne;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TexteSmsTest {

	@ParameterizedTest
	@CsvSource({
		"é, 160, 1", "é, 161, 2", "é, 306, 2", "é, 307, 3",
		"€, 80, 1", "€, 81, 2", "€, 153, 3",
		"ç, 70, 1", "ç, 71, 2", "ç, 134, 2", "ç, 135, 3",
		"🎤, 35, 1", "🎤, 36, 2", "🎤, 67, 3"
	})
	void compteLesUnitesEtGardeLesCaracteresEntiers(String caractere, int repetitions, int segments) {
		assertThat(TexteSms.segments(caractere.repeat(repetitions))).isEqualTo(segments);
	}

	@Test
	void ajouteLaMentionAuTexteNettoye() {
		assertThat(TexteSms.preparer(TexteSms.nettoyer("  Quiz jeudi !  "), "https://croustyquizz.invalid/stop/test"))
				.isEqualTo("Quiz jeudi !\nSTOP : https://croustyquizz.invalid/stop/test");
	}

	@Test
	void refuseUnTexteVideOuTropLongMentionComprise() {
		assertThatThrownBy(() -> TexteSms.nettoyer(" \n ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TexteSms.preparer("a".repeat(918), TexteSms.LIEN_EXEMPLE))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
