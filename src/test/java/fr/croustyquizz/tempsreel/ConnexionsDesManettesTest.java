package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConnexionsDesManettesTest {

	private final ConnexionsDesManettes connexions = new ConnexionsDesManettes();
	private final UUID lea = UUID.randomUUID();

	@Test
	void unJoueurEstConnecteDesSonArriveeSansConnexionOuverte() {
		assertThat(connexions.estConnecte(lea)).isTrue();
		assertThat(connexions.ouvrir(lea, "a")).isFalse();
		assertThat(connexions.estConnecte(lea)).isTrue();
	}

	@Test
	void seuleLaFermetureDeLaDerniereConnexionLeDeconnecte() {
		connexions.ouvrir(lea, "a");
		connexions.ouvrir(lea, "b");

		assertThat(connexions.fermer(lea, "a")).isFalse();
		assertThat(connexions.estConnecte(lea)).isTrue();
		assertThat(connexions.fermer(lea, "b")).isTrue();
		assertThat(connexions.estConnecte(lea)).isFalse();
	}

	@Test
	void uneNouvelleConnexionLeReconnecteUneSeuleFois() {
		connexions.ouvrir(lea, "a");
		connexions.fermer(lea, "a");

		assertThat(connexions.ouvrir(lea, "b")).isTrue();
		assertThat(connexions.ouvrir(lea, "c")).isFalse();
		assertThat(connexions.estConnecte(lea)).isTrue();
	}

	@Test
	void uneFermetureRepeteeOuInconnueNeChangeRien() {
		connexions.ouvrir(lea, "a");
		connexions.fermer(lea, "a");

		// Spring peut signaler deux fois la fin d'une même connexion.
		assertThat(connexions.fermer(lea, "a")).isFalse();
		assertThat(connexions.estConnecte(lea)).isFalse();
		UUID sam = UUID.randomUUID();
		assertThat(connexions.fermer(sam, "inconnue")).isFalse();
		assertThat(connexions.estConnecte(sam)).isTrue();
	}

	@Test
	void lesJoueursSontSuivisSeparement() {
		UUID sam = UUID.randomUUID();
		connexions.ouvrir(lea, "a");
		connexions.ouvrir(sam, "b");

		connexions.fermer(lea, "a");

		assertThat(connexions.estConnecte(lea)).isFalse();
		assertThat(connexions.estConnecte(sam)).isTrue();
	}
}
