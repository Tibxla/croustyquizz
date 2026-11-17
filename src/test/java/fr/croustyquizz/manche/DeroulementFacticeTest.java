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
		horloge.avancer(Duration.ofMinutes(5));
		assertThat(deroulement.estTerminee()).isTrue();
		assertThat(finsSignalees).isEqualTo(1);
	}

	@Test
	void laPauseGeleLeTempsRestant() {
		deroulement.demarrer();
		horloge.avancer(Duration.ofSeconds(5));
		deroulement.mettreEnPause(pilotage());
		Object avant = deroulement.instantane();
		horloge.avancer(Duration.ofHours(1));
		assertThat(deroulement.instantane()).isEqualTo(avant);
		deroulement.reprendre(pilotage());
		horloge.avancer(Duration.ofSeconds(14));
		assertThat(((DeroulementFactice.Etat) deroulement.instantane()).etape()).isEqualTo(1);
		horloge.avancer(Duration.ofSeconds(1));
		assertThat(((DeroulementFactice.Etat) deroulement.instantane()).etape()).isEqualTo(2);
	}

	@Test
	void passerEtapeCommenceLaSuivanteSansAttendre() {
		deroulement.demarrer();
		assertThat(deroulement.passerEtape(pilotage())).isInstanceOf(Accepte.class);
		assertThat(((DeroulementFactice.Etat) deroulement.instantane()).etape()).isEqualTo(2);
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
		deroulement.mettreEnPause(pilotage());

		deroulement.recevoir(uneAction(JOUEUR, horloge.maintenant()));

		assertThat(deroulement.points()).isEmpty();
	}

}
