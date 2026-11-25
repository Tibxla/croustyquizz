package fr.croustyquizz.blindtest;

import java.time.Duration;
import java.time.Instant;

/**
 * État de lecture de l'extrait de 30 s, mené par l'Horloge du serveur (ADR 0009, point 3) : en lecture depuis
 * {@code positionMs} à l'instant {@code depuisLe}, ou en pause à {@code positionMs} quand {@code depuisLe} est nul.
 * La position compte le temps d'extrait joué, hors pauses.
 */
public record LectureExtrait(long positionMs, Instant depuisLe) {

	/** Durée d'un extrait Deezer. */
	public static final long DUREE_MS = 30_000;

	public LectureExtrait {
		if (positionMs < 0 || positionMs > DUREE_MS) {
			throw new IllegalArgumentException("La position sort de l'extrait.");
		}
	}

	static LectureExtrait lancee(long positionMs, Instant depuisLe) {
		return new LectureExtrait(positionMs, depuisLe);
	}

	static LectureExtrait enPause(long positionMs) {
		return new LectureExtrait(positionMs, null);
	}

	public boolean enLecture() {
		return depuisLe != null;
	}

	/** Position atteinte à cet instant, bornée à la fin de l'extrait. */
	long positionA(Instant maintenant) {
		if (!enLecture()) {
			return positionMs;
		}
		long ecoule = Math.max(0, Duration.between(depuisLe, maintenant).toMillis());
		return Math.min(DUREE_MS, positionMs + ecoule);
	}

	Duration restantA(Instant maintenant) {
		return Duration.ofMillis(DUREE_MS - positionA(maintenant));
	}

	boolean estFinie() {
		return positionMs >= DUREE_MS;
	}

	LectureExtrait geleeA(Instant maintenant) {
		return enLecture() ? enPause(positionA(maintenant)) : this;
	}

	LectureExtrait lanceeA(Instant maintenant) {
		return enLecture() ? this : lancee(positionMs, maintenant);
	}

}
