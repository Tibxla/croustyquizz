package fr.croustyquizz.quiz;

import java.util.List;
import java.util.UUID;

public record QuestionPublique(
		UUID etapeId,
		UUID questionId,
		int numero,
		int total,
		String enonce,
		List<PropositionPublique> propositions
) {
}
