package fr.croustyquizz.manche;

import java.util.UUID;

/**
 * État de la Soirée vu du socle (#111), pour le Palmarès de fin de Soirée (#118) et
 * l'enchaînement automatique des Manches (#123). Implémenté par {@code soiree} :
 * {@code manche} ne dépend jamais de {@code soiree}.
 */
public interface EtatDeSoiree {

	/** La Soirée est connue, quel que soit son état. */
	boolean existe(UUID soireeId);

	/** La Soirée est lancée et pas encore terminée. */
	boolean estEnCours(UUID soireeId);

	/** La Soirée est terminée. */
	boolean estTerminee(UUID soireeId);

}
