package fr.croustyquizz.tempsreel;

import java.util.UUID;
import java.util.function.Function;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.ModeDeJeu;

/** Bloc immuable propre à un mode et à une destination, lu sous le verrou de Soirée. */
public interface FournisseurDeBloc {
	ModeDeJeu mode();
	Object fournir(UUID soireeId, Destination destination);

	/** Prépare les données partagées par les destinations d'un même assemblage. */
	default Function<Destination, Object> preparer(UUID soireeId) {
		return destination -> fournir(soireeId, destination);
	}
}
