package fr.croustyquizz.soiree;

/** Pseudo vide ou trop long. */
public class PseudoInvalide extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PseudoInvalide() {
		super("Le pseudo doit faire de 1 à " + Joueur.LONGUEUR_MAX_PSEUDO + " caractères");
	}

}
