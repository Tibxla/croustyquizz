package fr.croustyquizz.manche;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Suite de la Soirée à l'échéance d'une transition du Pilotage Automatique (#123), avec l'effet de
 * {@code piloterSoiree} {@code MANCHE_SUIVANTE} : démarrer la première Manche prête du programme, ou terminer la
 * Soirée quand il est épuisé. Implémenté par {@code soiree} ; {@code manche} ne dépend jamais de {@code soiree}.
 */
@FunctionalInterface
public interface EnchainementDeSoiree {

	/**
	 * Enchaîne après {@code mancheTermineeId}. Le reçu est tiré de cette Manche : un rappel répété ne démarre pas
	 * deux Manches. Lève une exception si l'enchaînement est refusé ou échoue.
	 */
	void mancheSuivante(UUID soireeId, UUID mancheTermineeId);

	/** Animateur au nom duquel le Pilotage Automatique enchaîne : le même pour chaque rappel de la Soirée. */
	static Acteur acteur(UUID soireeId) {
		UUID identifiant = UUID.nameUUIDFromBytes(("croustyquizz:pilotage-automatique:" + soireeId)
				.getBytes(StandardCharsets.UTF_8));
		return new Acteur(Acteur.Type.ANIMATEUR, identifiant, soireeId);
	}

}
