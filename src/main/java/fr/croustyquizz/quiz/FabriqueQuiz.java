package fr.croustyquizz.quiz;

import fr.croustyquizz.manche.ContexteDeManche;
import fr.croustyquizz.manche.DeroulementDeManche;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.FabriqueDeDeroulement;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.manche.ReglagesDeManche;
import java.time.Duration;
import java.util.List;

public final class FabriqueQuiz implements FabriqueDeDeroulement<ReglagesQuiz, EtatQuizPersiste> {

	private final SourceDeQuestions sourceDeQuestions;

	public FabriqueQuiz(SourceDeQuestions sourceDeQuestions) {
		this.sourceDeQuestions = sourceDeQuestions;
	}

	@Override
	public ModeDeJeu mode() {
		return ModeDeJeu.QUIZ;
	}

	@Override
	public Class<ReglagesQuiz> typeReglages() {
		return ReglagesQuiz.class;
	}

	@Override
	public ReglagesDeManche reglagesCommuns(ReglagesQuiz reglages) {
		return new ReglagesDeManche(reglages.nombreEtapes(), Duration.ofSeconds(reglages.tempsParEtapeSecondes()));
	}

	@Override
	public DeroulementQuiz creer(ContexteDeManche contexte, ReglagesQuiz reglages) {
		List<String> categories = reglages != null ? reglages.categories() : List.of();
		int nbEtapes = reglages != null ? reglages.nombreEtapes() : 1;
		VarianteQuiz variante = reglages != null ? reglages.variante() : VarianteQuiz.RAPIDITE;

		List<Question> questions = sourceDeQuestions != null
				? sourceDeQuestions.tirer(categories, Difficulte.FACILE, nbEtapes)
				: List.of();
		return new DeroulementQuiz(contexte, questions, variante);
	}

	@Override
	public DeroulementQuiz restaurer(ContexteDeManche contexte, ReglagesQuiz reglages,
			EtatQuizPersiste etat, EtatManche etatManche) {
		return new DeroulementQuiz(contexte, etat);
	}

	@Override
	public String typeEvenementMinuteur(DeroulementDeManche deroulement) {
		if (deroulement.estTerminee()) {
			return "manche-terminee";
		}
		if (deroulement.instantane() instanceof InstantaneQuiz instantane && instantane.phase() == PhaseQuiz.CORRECTION) {
			return "question-corrigee";
		}
		return "question-ouverte";
	}
}
