package fr.croustyquizz.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import fr.croustyquizz.manche.ActionJoueur;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.AttributionDePoints;
import fr.croustyquizz.manche.ContexteDeManche;
import fr.croustyquizz.manche.ContratDeroulementDeManche;
import fr.croustyquizz.manche.DeroulementDeManche;
import fr.croustyquizz.manche.ReglagesDeManche;
import fr.croustyquizz.manche.ResultatDeCommande;
import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.Refuse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeroulementQuizTest extends ContratDeroulementDeManche {

	private static final UUID AUTRE_JOUEUR = UUID.fromString("00000000-0000-0000-0000-00000000a002");

	private List<Question> questionsDeTest;

	@Override
	protected DeroulementDeManche creer(ContexteDeManche contexte) {
		questionsDeTest = List.of(
				creerQuestion("Capitale de la France ?", "Paris"),
				creerQuestion("Combien de cordes a une guitare standard ?", "6"),
				creerQuestion("Quel gaz compose principalement l'air ?", "Azote")
		);
		return new DeroulementQuiz(contexte, questionsDeTest);
	}

	@Override
	protected ActionJoueur uneAction(UUID joueurId, Instant recueLe) {
		Question premiere = questionsDeTest.getFirst();
		UUID etapeId = ((DeroulementQuiz) deroulement).etapeCouranteId();
		return new EnvoyerReponse(
				new Acteur(Acteur.Type.JOUEUR, joueurId, SOIREE),
				recueLe,
				UUID.randomUUID(),
				etapeId,
				premiere.propositionJusteId()
		);
	}

	private Question creerQuestion(String enonce, String bonneReponse) {
		UUID pJuste = UUID.randomUUID();
		List<Proposition> propositions = List.of(
				new Proposition(pJuste, bonneReponse),
				new Proposition(UUID.randomUUID(), "Faux 1"),
				new Proposition(UUID.randomUUID(), "Faux 2"),
				new Proposition(UUID.randomUUID(), "Faux 3")
		);
		return new Question(UUID.randomUUID(), enonce, "Culture générale", Difficulte.FACILE, propositions, pJuste);
	}

	@Test
	void demarrerOuvreLaPremiereQuestionSansDivulguerLaBonneReponse() {
		deroulement.demarrer();

		InstantaneQuiz instantane = (InstantaneQuiz) deroulement.instantane();
		assertThat(instantane.phase()).isEqualTo(PhaseQuiz.QUESTION_OUVERTE);
		assertThat(instantane.question()).isNotNull();
		assertThat(instantane.question().numero()).isEqualTo(1);
		assertThat(instantane.question().total()).isEqualTo(3);
		assertThat(instantane.question().enonce()).isEqualTo("Capitale de la France ?");
		assertThat(instantane.question().propositions()).hasSize(4);
		assertThat(instantane.ouverteLe()).isEqualTo(horloge.maintenant());
		assertThat(instantane.echeanceLe()).isEqualTo(horloge.maintenant().plus(Duration.ofSeconds(20)));
		assertThat(instantane.correction()).isNull();
	}

	@Test
	void expirationDuMinuteurPasseEnCorrectionEtCalculeLesPoints() {
		deroulement.demarrer();
		UUID etapeId = ((DeroulementQuiz) deroulement).etapeCouranteId();
		Question q1 = questionsDeTest.getFirst();

		// JOUEUR répond juste après 5 secondes (sur 20 secondes)
		horloge.avancer(Duration.ofSeconds(5));
		EnvoyerReponse reponseJuste = new EnvoyerReponse(
				new Acteur(Acteur.Type.JOUEUR, JOUEUR, SOIREE),
				horloge.maintenant(),
				UUID.randomUUID(),
				etapeId,
				q1.propositionJusteId()
		);
		ResultatDeCommande resultat = deroulement.recevoir(reponseJuste);
		assertThat(resultat).isInstanceOf(Accepte.class);
		Accepte accepte = (Accepte) resultat;
		assertThat(accepte.statutHttp()).isEqualTo(200);

		// AUTRE_JOUEUR répond faux
		UUID fausse = q1.propositions().stream()
				.filter(p -> !p.id().equals(q1.propositionJusteId()))
				.findFirst().orElseThrow().id();
		EnvoyerReponse reponseFausse = new EnvoyerReponse(
				new Acteur(Acteur.Type.JOUEUR, AUTRE_JOUEUR, SOIREE),
				horloge.maintenant(),
				UUID.randomUUID(),
				etapeId,
				fausse
		);
		deroulement.recevoir(reponseFausse);

		// Les points ne sont PAS attribués avant la clôture/correction
		assertThat(deroulement.points()).isEmpty();

		// On avance le temps pour atteindre l'échéance (encore 15 secondes)
		horloge.avancer(Duration.ofSeconds(15));

		InstantaneQuiz instantane = (InstantaneQuiz) deroulement.instantane();
		assertThat(instantane.phase()).isEqualTo(PhaseQuiz.CORRECTION);
		assertThat(instantane.correction()).isNotNull();
		assertThat(instantane.correction().propositionJusteId()).isEqualTo(q1.propositionJusteId());

		// Vérification des points attribués : JOUEUR a des points, AUTRE_JOUEUR en a 0
		assertThat(deroulement.points())
				.hasSize(1)
				.allSatisfy(p -> assertThat(p.concurrentId()).isEqualTo(JOUEUR));
		int pointsJoueur = deroulement.points().getFirst().valeur();
		assertThat(pointsJoueur).isGreaterThan(50);
	}

	@Test
	void uneReponsePlusRapideRapportePlusDePoints() {
		deroulement.demarrer();
		UUID etapeId = ((DeroulementQuiz) deroulement).etapeCouranteId();
		Question q1 = questionsDeTest.getFirst();

		// JOUEUR répond après 2 secondes
		horloge.avancer(Duration.ofSeconds(2));
		deroulement.recevoir(new EnvoyerReponse(
				new Acteur(Acteur.Type.JOUEUR, JOUEUR, SOIREE),
				horloge.maintenant(),
				UUID.randomUUID(),
				etapeId,
				q1.propositionJusteId()
		));

		// AUTRE_JOUEUR répond juste mais après 18 secondes
		horloge.avancer(Duration.ofSeconds(16));
		deroulement.recevoir(new EnvoyerReponse(
				new Acteur(Acteur.Type.JOUEUR, AUTRE_JOUEUR, SOIREE),
				horloge.maintenant(),
				UUID.randomUUID(),
				etapeId,
				q1.propositionJusteId()
		));

		// Clôture
		horloge.avancer(Duration.ofSeconds(2));

		List<AttributionDePoints> points = deroulement.points();
		int pointsJoueur1 = points.stream().filter(p -> p.concurrentId().equals(JOUEUR)).findFirst().orElseThrow().valeur();
		int pointsJoueur2 = points.stream().filter(p -> p.concurrentId().equals(AUTRE_JOUEUR)).findFirst().orElseThrow().valeur();

		assertThat(pointsJoueur1).isGreaterThan(pointsJoueur2);
	}

	@Test
	void memeEnvoiIdRetrouveLeMemeAccuseMemeApresCloture() {
		deroulement.demarrer();
		UUID etapeId = ((DeroulementQuiz) deroulement).etapeCouranteId();
		Question q1 = questionsDeTest.getFirst();

		UUID envoiId = UUID.randomUUID();
		EnvoyerReponse envoi = new EnvoyerReponse(
				new Acteur(Acteur.Type.JOUEUR, JOUEUR, SOIREE),
				horloge.maintenant(),
				envoiId,
				etapeId,
				q1.propositionJusteId()
		);

		ResultatDeCommande premier = deroulement.recevoir(envoi);
		assertThat(premier).isInstanceOf(Accepte.class);

		// Répéter immédiatement
		ResultatDeCommande repetition = deroulement.recevoir(envoi);
		assertThat(repetition).isInstanceOf(Accepte.class);
		assertThat(((Accepte) repetition).accuse()).isEqualTo(((Accepte) premier).accuse());

		// Avancer le temps pour clôturer la question
		horloge.avancer(Duration.ofSeconds(20));

		// Répéter après clôture
		ResultatDeCommande apresCloture = deroulement.recevoir(envoi);
		assertThat(apresCloture).isInstanceOf(Accepte.class);
		assertThat(((Accepte) apresCloture).accuse()).isEqualTo(((Accepte) premier).accuse());
	}

	@Test
	void refuseDeModifierUneReponseDejaAcceptee() {
		deroulement.demarrer();
		UUID etapeId = ((DeroulementQuiz) deroulement).etapeCouranteId();
		Question q1 = questionsDeTest.getFirst();

		EnvoyerReponse premiereReponse = new EnvoyerReponse(
				new Acteur(Acteur.Type.JOUEUR, JOUEUR, SOIREE),
				horloge.maintenant(),
				UUID.randomUUID(),
				etapeId,
				q1.propositions().get(0).id()
		);
		assertThat(deroulement.recevoir(premiereReponse)).isInstanceOf(Accepte.class);

		// Tentative de modification avec un nouvel envoiId
		EnvoyerReponse modification = new EnvoyerReponse(
				new Acteur(Acteur.Type.JOUEUR, JOUEUR, SOIREE),
				horloge.maintenant(),
				UUID.randomUUID(),
				etapeId,
				q1.propositions().get(1).id()
		);
		ResultatDeCommande refus = deroulement.recevoir(modification);
		assertThat(refus).isInstanceOfSatisfying(Refuse.class, r -> {
			assertThat(r.statutHttp()).isEqualTo(409);
			assertThat(r.code()).isEqualTo("REPONSE_DEJA_ENREGISTREE");
		});
	}

	@Test
	void refuseUneReponseHorsDelaiSelonLHeureDuServeur() {
		deroulement.demarrer();
		UUID etapeId = ((DeroulementQuiz) deroulement).etapeCouranteId();
		Question q1 = questionsDeTest.getFirst();

		// Le temps serveur dépasse l'échéance (20 secondes)
		horloge.avancer(Duration.ofSeconds(21));

		EnvoyerReponse envoiTardif = new EnvoyerReponse(
				new Acteur(Acteur.Type.JOUEUR, JOUEUR, SOIREE),
				horloge.maintenant(),
				UUID.randomUUID(),
				etapeId,
				q1.propositionJusteId()
		);
		ResultatDeCommande refus = deroulement.recevoir(envoiTardif);
		assertThat(refus).isInstanceOfSatisfying(Refuse.class, r -> {
			assertThat(r.statutHttp()).isEqualTo(409);
			assertThat(r.code()).isEqualTo("HORS_DELAI");
		});
	}

	@Test
	void passerEtapeDepuisCorrectionPasseALaQuestionSuivanteEtFinALaFin() {
		deroulement.demarrer();

		// Q1 ouverte -> clôture
		horloge.avancer(Duration.ofSeconds(20));
		assertThat(((InstantaneQuiz) deroulement.instantane()).phase()).isEqualTo(PhaseQuiz.CORRECTION);
		assertThat(((InstantaneQuiz) deroulement.instantane()).question().numero()).isEqualTo(1);

		// Passer étape -> Q2 ouverte
		deroulement.passerEtape(pilotage());
		assertThat(((InstantaneQuiz) deroulement.instantane()).phase()).isEqualTo(PhaseQuiz.QUESTION_OUVERTE);
		assertThat(((InstantaneQuiz) deroulement.instantane()).question().numero()).isEqualTo(2);

		// Q2 expire -> Correction Q2
		horloge.avancer(Duration.ofSeconds(20));
		assertThat(((InstantaneQuiz) deroulement.instantane()).phase()).isEqualTo(PhaseQuiz.CORRECTION);

		// Passer étape -> Q3 ouverte
		deroulement.passerEtape(pilotage());
		assertThat(((InstantaneQuiz) deroulement.instantane()).question().numero()).isEqualTo(3);

		// Q3 expire -> Correction Q3
		horloge.avancer(Duration.ofSeconds(20));
		assertThat(((InstantaneQuiz) deroulement.instantane()).phase()).isEqualTo(PhaseQuiz.CORRECTION);

		// Passer étape après dernière question -> fin de manche
		deroulement.passerEtape(pilotage());
		assertThat(deroulement.estTerminee()).isTrue();
		assertThat(finsSignalees).isEqualTo(1);
	}
}
