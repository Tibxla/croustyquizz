package fr.croustyquizz.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import fr.croustyquizz.manche.AttributionDePoints;
import fr.croustyquizz.manche.ContexteDeManche;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.HorlogeFactice;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.manche.ReglagesDeManche;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FabriqueQuizTest {

	private final HorlogeFactice horloge = new HorlogeFactice();
	private final UUID soireeId = UUID.randomUUID();
	private final UUID mancheId = UUID.randomUUID();
	private int fins;
	private final ContexteDeManche contexte = new ContexteDeManche(
			soireeId,
			mancheId,
			new ReglagesDeManche(3, Duration.ofSeconds(20)),
			horloge,
			() -> fins++
	);

	private Question creerQuestion() {
		UUID pJuste = UUID.randomUUID();
		return new Question(
				UUID.randomUUID(),
				"Énoncé ?",
				"Thème",
				Difficulte.FACILE,
				List.of(
						new Proposition(pJuste, "Juste"),
						new Proposition(UUID.randomUUID(), "Faux 1"),
						new Proposition(UUID.randomUUID(), "Faux 2"),
						new Proposition(UUID.randomUUID(), "Faux 3")
				),
				pJuste
		);
	}

	@Test
	void declareLeModeEtReglagesCommuns() {
		SourceDeQuestions source = (cats, diff, n) -> List.of(creerQuestion(), creerQuestion());
		FabriqueQuiz fabrique = new FabriqueQuiz(source);

		assertThat(fabrique.mode()).isEqualTo(ModeDeJeu.QUIZ);
		assertThat(fabrique.typeReglages()).isEqualTo(ReglagesQuiz.class);
		assertThat(fabrique.reglagesCommuns(new ReglagesQuiz(5, Duration.ofSeconds(15))))
				.isEqualTo(new ReglagesDeManche(5, Duration.ofSeconds(15)));
	}

	@Test
	void restaureLInstantaneEtLesPoints() {
		Question q = creerQuestion();
		UUID etapeId = UUID.randomUUID();
		UUID joueurId = UUID.randomUUID();
		Instant ouverteLe = horloge.maintenant();
		Instant echeanceLe = ouverteLe.plusSeconds(20);

		EtatQuizPersiste etat = new EtatQuizPersiste(
				List.of(q),
				0,
				etapeId,
				PhaseQuiz.QUESTION_OUVERTE,
				ouverteLe,
				echeanceLe,
				null,
				Map.of(joueurId, q.propositionJusteId()),
				Map.of(),
				List.of(new AttributionDePoints(joueurId, 85, "Bonne réponse"))
		);

		FabriqueQuiz fabrique = new FabriqueQuiz(null);
		DeroulementQuiz deroulement = fabrique.restaurer(contexte, new ReglagesQuiz(1, Duration.ofSeconds(20)), etat, EtatManche.EN_COURS);

		InstantaneQuiz instantane = (InstantaneQuiz) deroulement.instantane();
		assertThat(instantane.phase()).isEqualTo(PhaseQuiz.QUESTION_OUVERTE);
		assertThat(instantane.question().enonce()).isEqualTo("Énoncé ?");
		assertThat(deroulement.points()).hasSize(1);
		assertThat(deroulement.points().getFirst().valeur()).isEqualTo(85);
	}
}
