package fr.croustyquizz.manche;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Manche préparée hors du verrou de la Soirée, avec ses éventuels appels externes (ADR 0006), puis enregistrée sous
 * le verrou dans la transaction qui compose le programme.
 *
 * @param manche        Manche prête à enregistrer
 * @param donneesDuMode données propres au mode, enregistrées juste après la Manche
 */
public record AjoutDeManche(ManchePersistante manche, Consumer<ManchePersistante> donneesDuMode) {

	public AjoutDeManche {
		Objects.requireNonNull(manche, "manche");
		Objects.requireNonNull(donneesDuMode, "donneesDuMode");
	}

	/** À appeler sous le verrou de la Soirée, dans une transaction. */
	public void enregistrer(DepotDeManches depot) {
		depot.enregistrer(manche);
		donneesDuMode.accept(manche);
	}

}
