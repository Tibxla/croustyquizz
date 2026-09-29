package fr.croustyquizz.manche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DeroulementFacticeTest extends ContratDeroulementDeManche {

	private static final UUID AUTRE_JOUEUR = UUID.fromString("00000000-0000-0000-0000-00000000a002");

	@Override
	protected DeroulementDeManche creer(ContexteDeManche contexte) {
		return new DeroulementFactice(contexte);
	}

	@Override
	protected ActionJoueur uneAction(UUID joueurId, Instant recueLe) {
		return new DeroulementFactice.ActionFactice(joueurId, recueLe);
	}

	@Test
	void enchaineLesEtapesPuisSignaleLaFinUneSeuleFois() {
		deroulement.demarrer();
		assertThat(diffuseur.typesSalle()).containsExactly("etape-commencee");

		horloge.avancer(Duration.ofSeconds(20));
		assertThat(diffuseur.typesSalle()).containsExactly("etape-commencee", "etape-commencee");

		horloge.avancer(Duration.ofMinutes(5));
		assertThat(diffuseur.typesSalle())
				.containsExactly("etape-commencee", "etape-commencee", "etape-commencee", "manche-terminee");
		assertThat(deroulement.estTerminee()).isTrue();
		assertThat(finsSignalees).isEqualTo(1);
	}

	@Test
	void laPauseGeleLeTempsRestant() {
		deroulement.demarrer();
		horloge.avancer(Duration.ofSeconds(5));

		deroulement.mettreEnPause();
		horloge.avancer(Duration.ofHours(1));
		assertThat(diffuseur.typesSalle()).hasSize(1);

		deroulement.reprendre();
		horloge.avancer(Duration.ofSeconds(14));
		assertThat(diffuseur.typesSalle()).hasSize(1);
		horloge.avancer(Duration.ofSeconds(1));
		assertThat(diffuseur.typesSalle()).hasSize(2);
	}

	@Test
	void passerEtapeCommenceLaSuivanteSansAttendre() {
		deroulement.demarrer();

		deroulement.passerEtape();

		assertThat(diffuseur.typesSalle()).containsExactly("etape-commencee", "etape-commencee");
	}

	@Test
	void seuleLaPremiereActionDeLEtapeRapporteUnPoint() {
		deroulement.demarrer();

		deroulement.recevoir(uneAction(JOUEUR, horloge.maintenant()));
		deroulement.recevoir(uneAction(AUTRE_JOUEUR, horloge.maintenant()));

		assertThat(deroulement.points())
				.extracting(AttributionDePoints::concurrentId, AttributionDePoints::valeur)
				.containsExactly(tuple(JOUEUR, 1));
	}

	@Test
	void ignoreLesActionsPendantLaPause() {
		deroulement.demarrer();
		deroulement.mettreEnPause();

		deroulement.recevoir(uneAction(JOUEUR, horloge.maintenant()));

		assertThat(deroulement.points()).isEmpty();
	}

}
