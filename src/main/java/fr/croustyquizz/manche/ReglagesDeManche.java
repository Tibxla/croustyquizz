package fr.croustyquizz.manche;

import java.time.Duration;

/**
 * Réglages communs à tous les Modes de jeu. Chaque mode peut ajouter les siens.
 *
 * @param nombreEtapes  nombre de Questions, de Morceaux ou de Passages
 * @param tempsParEtape temps laissé aux Joueurs pour chaque étape
 */
public record ReglagesDeManche(int nombreEtapes, Duration tempsParEtape) {

	public ReglagesDeManche {
		if (nombreEtapes < 1) {
			throw new IllegalArgumentException("Une Manche compte au moins une étape");
		}
		if (tempsParEtape.isNegative() || tempsParEtape.isZero()) {
			throw new IllegalArgumentException("Le temps par étape doit être positif");
		}
	}

}
