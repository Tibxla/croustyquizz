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
	 * Appelé une fois par assemblage, sous le verrou, à la première destination concernée ; le champ préparé sert
	 * ensuite chaque destination de cet assemblage. Par défaut, il appelle {@link #fournir} pour chacune : un
	 * fournisseur dont la valeur ne dépend pas de la destination lit la base une seule fois en la redéfinissant.
	 */
	default ChampPrepare preparer(UUID soireeId) {
		return (destination, partieCommune) -> fournir(soireeId, destination, partieCommune);
	}

	/** Valeur du champ pour une destination de l'assemblage en cours. */
	@FunctionalInterface
	interface ChampPrepare {
		Object fournir(Destination destination, Map<String, Object> partieCommune);
	}
}
