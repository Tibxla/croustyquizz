package fr.croustyquizz.blindtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TirageDeMorceauxTest {

	private static final Morceau HALO = new Morceau(1, "Halo", "Beyoncé", null);
	private static final Morceau HALO_REMASTER = new Morceau(11, "Halo", "Beyoncé", null);
	private static final Morceau GET_LUCKY = new Morceau(2, "Get Lucky", "Daft Punk", null);
	private static final Morceau RESPECT = new Morceau(3, "Respect", "Aretha Franklin", null);
	private static final Morceau DANCING_QUEEN = new Morceau(4, "Dancing Queen", "ABBA", null);

	private final FauxCatalogueMusical catalogue = new FauxCatalogueMusical()
			.avecPlaylist(908_622_995L, HALO, GET_LUCKY, RESPECT, DANCING_QUEEN)
			.avecGenre(132, HALO, HALO_REMASTER, HALO, GET_LUCKY);

	@Test
	void tireLeNombreDemandeSansDoublonDansLaPlaylist() {
		TirageDeMorceaux tirage = new TirageDeMorceaux(catalogue, new Random(7));

		List<Morceau> tires = tirage.tirer(reglages(SourceDeMorceaux.Type.PLAYLIST, 908_622_995L, 3));

		assertThat(tires).hasSize(3).doesNotHaveDuplicates()
				.allSatisfy(morceau -> assertThat(List.of(HALO, GET_LUCKY, RESPECT, DANCING_QUEEN)).contains(morceau));
	}

	@Test
	void leHasardChangeLOrdreEtLaSelection() {
		ReglagesBlindTest reglages = reglages(SourceDeMorceaux.Type.PLAYLIST, 908_622_995L, 4);

		List<Morceau> premier = new TirageDeMorceaux(catalogue, new Random(1)).tirer(reglages);
		List<Morceau> autre = new TirageDeMorceaux(catalogue, new Random(2)).tirer(reglages);

		assertThat(premier).containsExactlyInAnyOrderElementsOf(autre).isNotEqualTo(autre);
	}

	@Test
	void compteDeuxVersionsDuMemeTitreCommeUnDoublon() {
		TirageDeMorceaux tirage = new TirageDeMorceaux(catalogue, new Random(3));

		List<Morceau> tires = tirage.tirer(reglages(SourceDeMorceaux.Type.GENRE, 132, 2));

		assertThat(tires).extracting(Morceau::titre).containsExactlyInAnyOrder("Halo", "Get Lucky");
		assertThatThrownBy(() -> tirage.tirer(reglages(SourceDeMorceaux.Type.GENRE, 132, 3)))
				.isInstanceOf(SourceMusicaleInvalide.class)
				.hasMessage("Ce genre n’a que 2 Morceaux jouables pour 3 demandés : demandes-en moins ou choisis une autre source.");
	}

	@Test
	void refuseUneSourceSansAssezDeMorceauxJouables() {
		TirageDeMorceaux tirage = new TirageDeMorceaux(catalogue, new Random(4));

		assertThatThrownBy(() -> tirage.tirer(reglages(SourceDeMorceaux.Type.PLAYLIST, 908_622_995L, 10)))
				.isInstanceOf(SourceMusicaleInvalide.class)
				.hasMessage("Cette playlist n’a que 4 Morceaux jouables pour 10 demandés : demandes-en moins ou choisis une autre source.");
	}

	@Test
	void nommeLeCasDUnSeulOuDAucunMorceauJouable() {
		FauxCatalogueMusical petit = new FauxCatalogueMusical().avecPlaylist(1, HALO);
		TirageDeMorceaux tirage = new TirageDeMorceaux(petit, new Random(6));

		assertThatThrownBy(() -> tirage.tirer(reglages(SourceDeMorceaux.Type.PLAYLIST, 1, 5)))
				.hasMessage("Cette playlist n’a qu’un Morceau jouable pour 5 demandés : demandes-en moins ou choisis une autre source.");
		assertThatThrownBy(() -> tirage.tirer(reglages(SourceDeMorceaux.Type.PLAYLIST, 404, 1)))
				.isInstanceOf(SourceMusicaleInvalide.class)
				.hasMessage("Cette playlist n’a aucun Morceau jouable : choisis une autre source.");
	}

	@Test
	void laisseRemonterUnCatalogueEnPanne() {
		TirageDeMorceaux tirage = new TirageDeMorceaux(catalogue.enPanne(), new Random(5));

		assertThatThrownBy(() -> tirage.tirer(reglages(SourceDeMorceaux.Type.PLAYLIST, 908_622_995L, 1)))
				.isInstanceOf(SourceMusicaleIndisponible.class);
	}

	@Test
	void bornesLesReglages() {
		SourceDeMorceaux source = new SourceDeMorceaux(SourceDeMorceaux.Type.GENRE, 132);

		assertThatThrownBy(() -> new ReglagesBlindTest(source, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ReglagesBlindTest(source, 51)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SourceDeMorceaux(SourceDeMorceaux.Type.PLAYLIST, -1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static ReglagesBlindTest reglages(SourceDeMorceaux.Type type, long id, int nombre) {
		return new ReglagesBlindTest(new SourceDeMorceaux(type, id), nombre);
	}

}
