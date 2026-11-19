package fr.croustyquizz.manche;

import java.util.UUID;

/**
 * État de la Soirée vu du socle (#111), pour le Palmarès de fin de Soirée (#23) et
 * l'enchaînement automatique des Manches (#25). Implémenté par {@code soiree} :
 * {@code manche} ne dépend jamais de {@code soiree}.
 */
public interface EtatDeSoiree {

	/** La Soirée est lancée et pas encore terminée. */
	boolean estEnCours(UUID soireeId);

	/** La Soirée est terminée. */
	boolean estTerminee(UUID soireeId);

}
