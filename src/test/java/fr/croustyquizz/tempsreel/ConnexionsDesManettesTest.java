package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConnexionsDesManettesTest {

	private final ConnexionsDesManettes connexions = new ConnexionsDesManettes();
	private final UUID soiree = UUID.randomUUID();
	private final UUID lea = UUID.randomUUID();

	@Test
	void unJoueurEstConnecteDesSonArriveeSansConnexionOuverte() {
		assertThat(connexions.estConnecte(lea)).isTrue();
		assertThat(connexions.ouvrir(soiree, lea, "a")).isFalse();
		assertThat(connexions.estConnecte(lea)).isTrue();
	}

	@Test
	void seuleLaFermetureDeLaDerniereConnexionLeDeconnecte() {
		connexions.ouvrir(soiree, lea, "a");
		connexions.ouvrir(soiree, lea, "b");

		assertThat(connexions.fermer(soiree, lea, "a")).isFalse();
		assertThat(connexions.estConnecte(lea)).isTrue();
		assertThat(connexions.fermer(soiree, lea, "b")).isTrue();
		assertThat(connexions.estConnecte(lea)).isFalse();
	}

	@Test
	void uneNouvelleConnexionLeReconnecteUneSeuleFois() {
		connexions.ouvrir(soiree, lea, "a");
		connexions.fermer(soiree, lea, "a");

		assertThat(connexions.ouvrir(soiree, lea, "b")).isTrue();
		assertThat(connexions.ouvrir(soiree, lea, "c")).isFalse();
		assertThat(connexions.estConnecte(lea)).isTrue();
	}

	@Test
	void uneFermetureRepeteeOuInconnueNeChangeRien() {
		connexions.ouvrir(soiree, lea, "a");
		connexions.fermer(soiree, lea, "a");

		// Spring peut signaler deux fois la fin d'une même connexion.
		assertThat(connexions.fermer(soiree, lea, "a")).isFalse();
		assertThat(connexions.estConnecte(lea)).isFalse();
		UUID sam = UUID.randomUUID();
		assertThat(connexions.fermer(soiree, sam, "inconnue")).isFalse();
		assertThat(connexions.estConnecte(sam)).isTrue();
	}

	@Test
	void lesJoueursSontSuivisSeparement() {
		UUID sam = UUID.randomUUID();
		connexions.ouvrir(soiree, lea, "a");
		connexions.ouvrir(soiree, sam, "b");

		connexions.fermer(soiree, lea, "a");

		assertThat(connexions.estConnecte(lea)).isFalse();
		assertThat(connexions.estConnecte(sam)).isTrue();
	}

	@Test
	void oublierUneSoireeEffaceSesDeconnectesEtSesConnexionsOuvertes() {
		UUID sam = UUID.randomUUID();
		connexions.ouvrir(soiree, lea, "a");
		connexions.fermer(soiree, lea, "a");
		connexions.ouvrir(soiree, sam, "b");

		connexions.oublier(soiree);

		assertThat(connexions.estConnecte(lea)).isTrue();
		assertThat(connexions.suitLaSoiree(soiree)).isFalse();
		// La Manette de Sam, encore ouverte à la fin, ne le remet pas hors ligne en se fermant.
		assertThat(connexions.fermer(soiree, sam, "b")).isFalse();
		assertThat(connexions.estConnecte(sam)).isTrue();
		assertThat(connexions.suitLaSoiree(soiree)).isFalse();
	}

	@Test
	void oublierUneSoireeNeTouchePasLesAutres() {
		UUID autre = UUID.randomUUID();
		UUID sam = UUID.randomUUID();
		connexions.ouvrir(soiree, lea, "a");
		connexions.fermer(soiree, lea, "a");
		connexions.ouvrir(autre, sam, "b");
		connexions.fermer(autre, sam, "b");

		connexions.oublier(soiree);
		connexions.oublier(UUID.randomUUID());

		assertThat(connexions.estConnecte(sam)).isFalse();
		assertThat(connexions.suitLaSoiree(autre)).isTrue();
		assertThat(connexions.ouvrir(autre, sam, "c")).isTrue();
	}
}
