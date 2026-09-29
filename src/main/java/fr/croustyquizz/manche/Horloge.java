package fr.croustyquizz.manche;

import java.time.Duration;
import java.time.Instant;

/**
 * Seule source de temps du code de jeu. Aucun Mode de jeu n'appelle
 * {@code Instant.now()} ni ne crée de thread : il passe par l'Horloge, qu'un test
 * remplace par une horloge factice avancée à la main.
 */
public interface Horloge {

	Instant maintenant();

	/**
	 * Programme une action après un délai. L'action s'exécute dans le même fil que
	 * les autres appels du déroulement (voir {@link DeroulementDeManche}).
	 */
	Minuteur programmer(Duration delai, Runnable action);

}
