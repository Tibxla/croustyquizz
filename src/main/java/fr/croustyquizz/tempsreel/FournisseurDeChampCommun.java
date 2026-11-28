package fr.croustyquizz.tempsreel;

import fr.croustyquizz.manche.Destination;
import java.util.Map;
import java.util.UUID;

/** Ajoute un champ de premier niveau sous le verrou de la Soirée. */
public interface FournisseurDeChampCommun {
	String champ();
	boolean concerne(Destination destination);
	/** Reçoit la partie commune en lecture seule. */
	Object fournir(UUID soireeId, Destination destination, Map<String, Object> partieCommune);
	/**
	 * Prépare le champ pour un assemblage : l'assembleur l'appelle une fois, à la première destination concernée,
	 * puis réutilise le résultat pour les suivantes. Par défaut, chaque destination repasse par {@link #fournir}.
	 */
	default ChampPrepare preparer(UUID soireeId) {
		return (destination, partieCommune) -> fournir(soireeId, destination, partieCommune);
	}

	/** Champ d'un assemblage, servi destination par destination. */
	@FunctionalInterface
	interface ChampPrepare {
		Object fournir(Destination destination, Map<String, Object> partieCommune);
	}
}
