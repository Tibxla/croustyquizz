package fr.croustyquizz.manche;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages communs aux Manches.
 *
 * @param dureeTransitionSecondes durée de la transition du Pilotage Automatique entre deux Manches (#123)
 */
@ConfigurationProperties("croustyquizz.manche")
public record ProprietesManche(@DefaultValue("15") long dureeTransitionSecondes) {

	public ProprietesManche {
		if (dureeTransitionSecondes <= 0) {
			throw new IllegalArgumentException("La durée de la transition entre Manches doit être positive.");
		}
	}

	public Duration dureeTransition() {
		return Duration.ofSeconds(dureeTransitionSecondes);
	}

}
