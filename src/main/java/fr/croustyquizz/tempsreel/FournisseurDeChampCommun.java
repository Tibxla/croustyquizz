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
}
