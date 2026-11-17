package fr.croustyquizz.quiz;

import java.time.Duration;

public record ReglagesQuiz(int nombreEtapes, int tempsParEtapeSecondes) {
	public Duration tempsParEtape() {
		return Duration.ofSeconds(tempsParEtapeSecondes);
	}
}
