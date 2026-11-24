package fr.croustyquizz.manche;

import java.util.UUID;

/**
 * Le Pilotage vaut pour toute la Soirée. Le package {@code soiree} fournit le
 * Pilotage des Soirées enregistrées lors de leur création (#6).
 * Les implémentations {@code @Fallback} servent uniquement aux Soirées absentes de la base.
 */
@FunctionalInterface
public interface PilotageDeSoiree {
	/** Vrai exactement quand la Soirée est en Pilotage Automatique. */
	boolean estAutomatique(UUID soireeId);
}
