package fr.croustyquizz.quiz;

import java.util.List;
import java.util.UUID;

public record CorrectionPublique(
		UUID propositionJusteId,
		List<ReponseDistribution> repartition
) {
}
