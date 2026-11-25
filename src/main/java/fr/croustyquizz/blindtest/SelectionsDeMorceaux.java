package fr.croustyquizz.blindtest;

import java.util.List;
import java.util.UUID;

/** Port vers la sélection tirée à l'ajout de la Manche et gardée par le service (#177), lue au démarrage. */
@FunctionalInterface
public interface SelectionsDeMorceaux {

	List<Morceau> de(UUID mancheId);

}
