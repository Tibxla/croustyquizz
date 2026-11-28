package fr.croustyquizz.blindtest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Batterie de la correction tolérante (ADR 0009, point 7 ; décisions du 28/11/2026 sur #18). */
class CorrecteurTolerantTest {

	private final CorrecteurTolerant correcteur = new CorrecteurTolerant();

	@ParameterizedTest(name = "« {0} » est juste pour « {1} »")
	@CsvSource({
		// Casse, accents et blancs, comme la correction exacte de #17.
		"halo, Halo",
		"HALO, Halo",
		"beyonce, Beyoncé",
		"Beyoncé, Beyoncé",
		"'  Crazy   in love ', Crazy in Love",
		"coeur de pirate, Cœur de pirate",
		// Article en tête, en français ou en anglais, d'un côté ou de l'autre.
		"la beyonce, Beyoncé",
		"weeknd, The Weeknd",
		"the weeknd, Weeknd",
		"amour toujours, L'Amour toujours",
		"les rita mitsouko, Rita Mitsouko",
		// Ponctuation ignorée, et « & », « et », « and » qui se valent.
		"Dont Stop Me Now, Don't Stop Me Now",
		"AC DC, AC/DC",
		"Simon and Garfunkel, Simon & Garfunkel",
		"simon et garfunkel, Simon & Garfunkel",
		"'Hey, Jude!', Hey Jude",
		// Espaces manquants ou en trop, sans consommer de faute.
		"DaftPunk, Daft Punk",
		"Bonjovi, Bon Jovi",
		"acdc, AC/DC",
		"aha, A-ha",
		// Fautes de frappe : 1 de 5 à 8 lettres, 2 à partir de 9.
		"Beyonse, Beyoncé",
		"Toxik, Toxic",
		"Beyocné, Beyoncé",
		"Bohemain Rapsody, Bohemian Rhapsody",
		"Daft Pnuk, Daft Punk",
		// Un nom fait d'articles reste entier.
		"the the, The The",
		"7 rings, 7 Rings",
	})
	void accepte(String saisie, String attendu) {
		assertThat(correcteur.estJuste(saisie, attendu)).isTrue();
	}

	@ParameterizedTest(name = "« {0} » est faux pour « {1} »")
	@CsvSource({
		// Jusqu'à 4 lettres, aucune faute permise.
		"Hello, Halo",
		"Hal, Halo",
		"Hallo, Halo",
		// Au-delà du seuil.
		"Let It Go, Let It Be",
		"Bohemain Rapsodi, Bohemian Rhapsody",
		"Beyonsée, Beyoncé",
		// Une autre réponse, ou une partie seulement.
		"Rihanna, Beyoncé",
		"Single Ladies, Halo",
		"Bohemian, Bohemian Rhapsody",
		// Les articles ne sont retirés qu'en tête.
		"Back in the Black, Back in Black",
		// Un article seul ne suffit jamais.
		"the, The Weeknd",
		"la, La Bamba",
		"the, The The",
		// Les chiffres restent des chiffres.
		"seven rings, 7 Rings",
		// Champ vide une fois normalisé.
		"'   ', Halo",
		"'', Halo",
		"'!?!', Halo",
	})
	void refuse(String saisie, String attendu) {
		assertThat(correcteur.estJuste(saisie, attendu)).isFalse();
	}

	@Test
	void refuseUnChampAbsent() {
		assertThat(correcteur.estJuste(null, "Halo")).isFalse();
		assertThat(correcteur.estJuste("Halo", null)).isFalse();
	}

	/**
	 * Limite connue et acceptée : à 5 lettres, une faute est permise, donc un autre mot voisin passe. Le seuil
	 * protège les noms de 4 lettres ou moins, pas tous les mots voisins.
	 */
	@Test
	void limiteConnueUnMotVoisinDeCinqLettresPasse() {
		assertThat(correcteur.estJuste("Tonic", "Toxic")).isTrue();
	}

	@Test
	void lInversionDeDeuxLettresNeCompteQuePourUneFaute() {
		assertThat(CorrecteurTolerant.distance("beyocne", "beyonce")).isEqualTo(1);
		assertThat(CorrecteurTolerant.distance("halo", "hello")).isEqualTo(2);
		assertThat(CorrecteurTolerant.distance("", "halo")).isEqualTo(4);
	}

	@ParameterizedTest(name = "{0} lettres : {1} faute(s)")
	@CsvSource({ "1, 0", "4, 0", "5, 1", "8, 1", "9, 2", "40, 2" })
	void fautesPermisesSelonLaLongueur(int longueur, int fautes) {
		assertThat(CorrecteurTolerant.fautesPermises(longueur)).isEqualTo(fautes);
	}

	@Test
	void laCleRapprocheDeuxEcrituresDuMemeTitre() {
		assertThat(CorrecteurTolerant.cle("Don't Stop Me Now")).isEqualTo(CorrecteurTolerant.cle("dont stop me now"));
		assertThat(CorrecteurTolerant.cle("Halo")).isNotEqualTo(CorrecteurTolerant.cle("Hello"));
	}

}
