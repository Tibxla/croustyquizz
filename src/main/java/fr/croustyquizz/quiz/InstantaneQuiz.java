package fr.croustyquizz.quiz;

import java.time.Instant;

public record InstantaneQuiz(
		PhaseQuiz phase,
		QuestionPublique question,
		Instant ouverteLe,
		Instant echeanceLe,
		Long tempsRestantMs,
		CorrectionPublique correction
) {
}
