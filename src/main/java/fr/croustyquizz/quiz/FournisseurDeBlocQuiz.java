package fr.croustyquizz.quiz;

import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.manche.ServiceDeManche;
import fr.croustyquizz.tempsreel.FournisseurDeBloc;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class FournisseurDeBlocQuiz implements FournisseurDeBloc {

	private static final String CLE_PHASE = "phase";
	private static final String CLE_QUESTION = "question";

	private final Supplier<ServiceDeManche<?, ?>> service;
	private final DepotDeManches depot;

	public FournisseurDeBlocQuiz(Supplier<ServiceDeManche<?, ?>> service, DepotDeManches depot) {
		this.service = service;
		this.depot = depot;
	}

	@Override
	public ModeDeJeu mode() {
		return ModeDeJeu.QUIZ;
	}

	@Override
	public Object fournir(UUID soireeId, Destination destination) {
		var manche = depot.deLaSoiree(soireeId).stream()
				.filter(m -> m.getEtat() == EtatManche.EN_COURS || m.getEtat() == EtatManche.EN_PAUSE)
				.findFirst()
				.orElse(null);
		if (manche == null) {
			return null;
		}

		Object instantaneObj = service.get().instantane(soireeId, manche.getMancheId());
		if (!(instantaneObj instanceof InstantaneQuiz instantane)) {
			return null;
		}

		if (instantane.phase() == PhaseQuiz.ATTENTE) {
			return Map.of(CLE_PHASE, "ATTENTE");
		}
		if (instantane.phase() == PhaseQuiz.TERMINEE) {
			return Map.of(CLE_PHASE, "TERMINEE");
		}

		var questionPub = instantane.question();
		var questionMap = Map.of(
				"etapeId", questionPub.etapeId(),
				"questionId", questionPub.questionId(),
				"numero", questionPub.numero(),
				"total", questionPub.total(),
				"enonce", questionPub.enonce(),
				"propositions", questionPub.propositions().stream()
						.map(p -> Map.of("id", p.id(), "texte", p.texte()))
						.toList()
		);

		if (instantane.phase() == PhaseQuiz.QUESTION_OUVERTE) {
			return Map.of(
					CLE_PHASE, "QUESTION_OUVERTE",
					CLE_QUESTION, questionMap,
					"ouverteLe", instantane.ouverteLe(),
					"echeanceLe", instantane.echeanceLe()
			);
		}

		if (instantane.phase() == PhaseQuiz.EN_PAUSE) {
			return Map.of(
					CLE_PHASE, "EN_PAUSE",
					CLE_QUESTION, questionMap,
					"tempsRestantMs", instantane.tempsRestantMs() != null ? instantane.tempsRestantMs() : 0L
			);
		}

		if (instantane.phase() == PhaseQuiz.CORRECTION) {
			var corr = instantane.correction();
			var repartitionList = corr.repartition().stream()
					.map(r -> Map.of("propositionId", r.propositionId(), "nombreReponses", r.nombreReponses()))
					.toList();
			var correctionMap = Map.of(
					"propositionJusteId", corr.propositionJusteId(),
					"repartition", repartitionList
			);
			return Map.of(
					CLE_PHASE, "CORRECTION",
					CLE_QUESTION, questionMap,
					"correction", correctionMap
			);
		}

		return null;
	}
}
