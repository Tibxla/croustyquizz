package fr.croustyquizz.quiz;

import java.time.Duration;
import java.util.List;

public record ReglagesQuiz(
		int nombreEtapes,
		int tempsParEtapeSecondes,
		VarianteQuiz variante,
		List<String> categories
) {
	public ReglagesQuiz(int nombreEtapes, int tempsParEtapeSecondes) {
		this(nombreEtapes, tempsParEtapeSecondes, VarianteQuiz.RAPIDITE, List.of());
	}

	public ReglagesQuiz {
		if (variante == null) {
			variante = VarianteQuiz.RAPIDITE;
		}
		if (categories == null) {
			categories = List.of();
		} else {
			categories = List.copyOf(categories);
		}
	}

	public Duration tempsParEtape() {
		return Duration.ofSeconds(tempsParEtapeSecondes);
	}
}
