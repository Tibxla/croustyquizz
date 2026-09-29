package fr.croustyquizz.manche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.croustyquizz.tempsreel.DiffuseurFactice;

/**
 * Tests que tout Mode de jeu doit passer. Pour s'en servir, la classe de test du
 * mode étend celle-ci et implémente {@link #creer} et {@link #uneAction} ; elle
 * hérite de ces tests en plus des siens. Exemple : {@code DeroulementFacticeTest}.
 */
public abstract class ContratDeroulementDeManche {

	protected static final UUID SOIREE = UUID.fromString("00000000-0000-0000-0000-00000000501e");
	protected static final UUID MANCHE = UUID.fromString("00000000-0000-0000-0000-0000000a4c4e");
	protected static final UUID JOUEUR = UUID.fromString("00000000-0000-0000-0000-00000000a001");

	protected final HorlogeFactice horloge = new HorlogeFactice();
	protected final DiffuseurFactice diffuseur = new DiffuseurFactice();
	protected int finsSignalees;
	protected DeroulementDeManche deroulement;

	/** Crée le déroulement du mode testé. */
	protected abstract DeroulementDeManche creer(ContexteDeManche contexte);

	/** Une action que le mode sait traiter pendant une étape. */
	protected abstract ActionJoueur uneAction(UUID joueurId, Instant recueLe);

	protected ReglagesDeManche reglages() {
		return new ReglagesDeManche(3, Duration.ofSeconds(20));
	}

	@BeforeEach
	void creerLeDeroulement() {
		ContexteDeManche contexte = new ContexteDeManche(
				SOIREE, MANCHE, reglages(), horloge, diffuseur, () -> finsSignalees++);
		deroulement = creer(contexte);
	}

	@Test
	void neSeTerminePasAuDemarrage() {
		deroulement.demarrer();

		assertThat(deroulement.estTerminee()).isFalse();
		assertThat(finsSignalees).isZero();
	}

	@Test
	void terminerArreteLaMancheSansSignalerDeFin() {
		deroulement.demarrer();

		deroulement.terminer();

		assertThat(deroulement.estTerminee()).isTrue();
		assertThat(finsSignalees).isZero();
	}

	@Test
	void ignoreLesActionsApresLaFin() {
		deroulement.demarrer();
		deroulement.terminer();
		List<AttributionDePoints> avant = deroulement.points();

		deroulement.recevoir(uneAction(JOUEUR, horloge.maintenant()));

		assertThat(deroulement.points()).isEqualTo(avant);
	}

	@Test
	void ignoreUneActionInconnueDuMode() {
		deroulement.demarrer();
		ActionJoueur inconnue = new ActionJoueur() {
			@Override
			public UUID joueurId() {
				return JOUEUR;
			}

			@Override
			public Instant recueLe() {
				return horloge.maintenant();
			}
		};

		assertThatCode(() -> deroulement.recevoir(inconnue)).doesNotThrowAnyException();
		assertThat(deroulement.points()).isEmpty();
	}

	@Test
	void resteTermineeQuoiQueFasseLAnimateur() {
		deroulement.demarrer();
		deroulement.terminer();

		assertThatCode(() -> {
			deroulement.mettreEnPause();
			deroulement.reprendre();
			deroulement.passerEtape();
			deroulement.terminer();
		}).doesNotThrowAnyException();
		assertThat(deroulement.estTerminee()).isTrue();
		assertThat(finsSignalees).isZero();
	}

	@Test
	void renvoieToujoursUneListeDePoints() {
		assertThat(deroulement.points()).isNotNull();
		deroulement.demarrer();
		assertThat(deroulement.points()).isNotNull();
	}

	@Test
	void neLaissePasDeMinuteurApresLaFin() {
		deroulement.demarrer();

		deroulement.terminer();

		assertThat(horloge.minuteursEnAttente()).isZero();
	}

}
