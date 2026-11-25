package fr.croustyquizz.quiz;

import java.util.List;
import java.util.UUID;

public record Question(
		UUID id,
		String enonce,
		String categorie,
		Difficulte difficulte,
		List<Proposition> propositions,
		UUID propositionJusteId
) {
	public Question {
		if (propositions == null || propositions.size() != 4) {
			throw new IllegalArgumentException("Une Question doit comporter exactement 4 propositions.");
		}
		if (propositions.stream().noneMatch(p -> p.id().equals(propositionJusteId))) {
			throw new IllegalArgumentException("La proposition juste doit appartenir aux propositions de la Question.");
		}
	}
}
