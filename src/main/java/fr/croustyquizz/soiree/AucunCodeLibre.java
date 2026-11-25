package fr.croustyquizz.soiree;

/**
 * Aucun code d'accès libre trouvé après tous les tirages prévus : passager,
 * l'Animateur peut réessayer l'ouverture (503 SERVICE_INDISPONIBLE).
 */
public class AucunCodeLibre extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public AucunCodeLibre(int essais) {
		super("Aucun code d'accès libre après " + essais + " essais");
	}

}
