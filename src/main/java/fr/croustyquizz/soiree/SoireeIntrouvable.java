package fr.croustyquizz.soiree;

import java.util.UUID;

/** Aucune Soirée ne porte cet identifiant. */
public class SoireeIntrouvable extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SoireeIntrouvable(UUID soireeId) {
		super("Aucune Soirée " + soireeId);
	}

}
