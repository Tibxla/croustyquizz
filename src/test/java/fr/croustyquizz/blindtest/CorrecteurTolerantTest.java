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
		// Une liaison collée aux mots voisins, dans chacune de ses écritures (relecture de #293, point 3).
		"SimonAndGarfunkel, Simon and Garfunkel",
		"simonetgarfunkel, Simon & Garfunkel",
		// Un nom fait de ponctuation se retrouve à l'identique (point 4).
		"!!!, !!!",
		// Le budget suit la distance non restreinte : « CA » devient « ABC » en deux fautes.
		"Rock CADE, Rock ABCDE",
		// Une saisie exacte avec signes vocaliques (point 5).
		"कला, कला",
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
		// L'article de tête n'augmente pas le budget : « cure » n'a que 4 lettres (relecture de #293, point 1).
		"The Pure, The Cure",
		"Pure, The Cure",
		// Un nom fait de ponctuation : ni une autre ponctuation, ni une saisie vide (point 4).
		"?, !!!",
		"'  ', !!!",
		// Les signes vocaliques ne sont pas de la ponctuation : « कल » n'est pas « कला » (point 5).
		"कल, कला",
		// « l » n'est un article que suivi d'une apostrophe : « OVE » n'est pas « L-O-V-E ».
		"OVE, L-O-V-E",
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

	@Test
	void laDistanceNEstPasRestreinteAuxInversionsIsolees() {
		assertThat(CorrecteurTolerant.distance("ca", "abc")).isEqualTo(2);
		assertThat(CorrecteurTolerant.distance("rockcade", "rockabcde")).isEqualTo(2);
	}

	/** Le budget compte les points de code, comme la distance : une lettre hors du plan de base compte pour une. */
	@Test
	void leBudgetCompteLesPointsDeCode() {
		String quatre = "𐐨𐐨𐐨𐐨";
		assertThat(quatre.length()).isEqualTo(8);
		assertThat(correcteur.estJuste("𐐨𐐨𐐨", quatre)).isFalse();
		assertThat(correcteur.estJuste(quatre, quatre)).isTrue();
	}

	@ParameterizedTest(name = "{0} lettres : {1} faute(s)")
	@CsvSource({ "1, 0", "4, 0", "5, 1", "8, 1", "9, 2", "40, 2" })
	void fautesPermisesSelonLaLongueur(int longueur, int fautes) {
		assertThat(CorrecteurTolerant.fautesPermises(longueur)).isEqualTo(fautes);
	}

	@Test
	void lesFormesExactesRapprochentDeuxEcrituresDuMemeTitre() {
		assertThat(CorrecteurTolerant.formesExactes("Don't Stop Me Now"))
				.containsAnyElementsOf(CorrecteurTolerant.formesExactes("dont stop me now"));
		assertThat(CorrecteurTolerant.formesExactes("The Cure"))
				.containsAnyElementsOf(CorrecteurTolerant.formesExactes("Cure"));
		assertThat(CorrecteurTolerant.formesExactes("Halo"))
				.doesNotContainAnyElementsOf(CorrecteurTolerant.formesExactes("Hello"));
	}

}
