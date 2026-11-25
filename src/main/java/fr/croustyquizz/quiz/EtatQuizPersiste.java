package fr.croustyquizz.quiz;

import fr.croustyquizz.manche.AttributionDePoints;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EtatQuizPersiste(
		List<Question> questions,
		int indexQuestion,
		UUID etapeCouranteId,
		PhaseQuiz phase,
		Instant ouverteLe,
		Instant echeanceLe,
		Long resteAuMomentDeLaPauseMs,
		Map<UUID, UUID> reponsesParJoueur,
		Map<UUID, AccuseReponse> envoisParEnvoiId,
		List<AttributionDePoints> points
) {
	public EtatQuizPersiste {
		questions = questions == null ? List.of() : List.copyOf(questions);
		reponsesParJoueur = reponsesParJoueur == null ? Map.of() : Map.copyOf(reponsesParJoueur);
		envoisParEnvoiId = envoisParEnvoiId == null ? Map.of() : Map.copyOf(envoisParEnvoiId);
		points = points == null ? List.of() : List.copyOf(points);
	}
}
