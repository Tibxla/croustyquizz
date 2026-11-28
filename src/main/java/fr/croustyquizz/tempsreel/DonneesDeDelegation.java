package fr.croustyquizz.tempsreel;

import java.util.UUID;

import fr.croustyquizz.manche.Pouvoir;

/**
 * Données qu'un pouvoir délégué apporte à la Manette (#67), lues sous le verrou de la Soirée. Elles ne
 * contiennent que ce que le pouvoir demande : jamais un secret de la Console.
 */
public interface DonneesDeDelegation {
	Pouvoir pouvoir();

	/** Nom du champ dans {@code delegation}. */
	String champ();

	Object fournir(UUID soireeId);
}
