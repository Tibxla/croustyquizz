package fr.croustyquizz.soiree;

/**
 * Le code saisi ne désigne aucune Soirée qu'on peut rejoindre : mal formé,
 * inconnu, ou celui d'une Soirée terminée (code {@code CODE_ACCES_INVALIDE} du contrat).
 */
public class CodeAccesInvalide extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public CodeAccesInvalide() {
		super("Aucune Soirée à rejoindre ne porte ce code d'accès");
	}

}
