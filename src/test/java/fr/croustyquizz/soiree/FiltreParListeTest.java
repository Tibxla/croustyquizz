package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Le filtre réel, avec sa liste, en Java pur. */
class FiltreParListeTest {

	private final FiltreDePseudos filtre = new FiltreParListe();

	@ParameterizedTest
	@ValueSource(strings = { "connard", "CONNARD", "Connard", "c0nn4rd", "Coooonnnard", "c.o.n.n.a.r.d",
			"SuperConnard", "Le Connard du 7", "énculé", "ENCULÉ", "s@l0pe", "Pu-tain", "f.d.p", "FDP", "Le con",
			"con", "pédé", "P3D3", "N1QU3", "merde", "Emmerdeur" })
	void refuseLesMotsInterditsMemeDeguises(String pseudo) {
		assertThat(filtre.estAcceptable(pseudo)).as(pseudo).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Constance", "Monique", "Culotte", "Bitebi", "Léa", "Inès", "Tom 2000", "Zoé ✨",
			"Les Pédales du Tour", "Cunégonde", "Nazaire", "Bicotte" })
	void accepteLesPseudosOrdinaires(String pseudo) {
		assertThat(filtre.estAcceptable(pseudo)).as(pseudo).isTrue();
	}

	@Test
	void unPseudoAbsentNEstPasAcceptable() {
		assertThat(filtre.estAcceptable(null)).isFalse();
	}

	@Test
	void laFormeRetireAccentsChiffresEtRepetitions() {
		assertThat(FiltreParListe.forme("  Éléonore-du-93 !! ")).isEqualTo("eleonore du e i");
		assertThat(FiltreParListe.forme("Coooonnnard")).isEqualTo("conard");
	}

}
