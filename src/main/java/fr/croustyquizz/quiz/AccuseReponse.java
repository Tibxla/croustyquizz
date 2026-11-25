package fr.croustyquizz.quiz;

import java.time.Instant;
import java.util.UUID;

public record AccuseReponse(
		UUID envoiId,
		UUID reponseId,
		UUID etapeId,
		UUID propositionId,
		Instant recueLe
) {
}
