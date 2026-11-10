package fr.croustyquizz.soiree;

/** Titre de Soirée absent, vide ou trop long. */
public class TitreInvalide extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public TitreInvalide() {
		super("Le titre doit faire de 1 à " + Soiree.LONGUEUR_MAX_TITRE + " caractères");
	}

}
