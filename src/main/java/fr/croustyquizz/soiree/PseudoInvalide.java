package fr.croustyquizz.soiree;

/** Pseudo vide ou trop long. */
public class PseudoInvalide extends RuntimeException {

	public PseudoInvalide() {
		super("Le pseudo doit faire de 1 à " + Joueur.LONGUEUR_MAX_PSEUDO + " caractères");
	}

}
