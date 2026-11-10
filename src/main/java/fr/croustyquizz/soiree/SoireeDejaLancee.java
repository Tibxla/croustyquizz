package fr.croustyquizz.soiree;

/** Une Soirée en cours ou terminée ne peut plus être ouverte. */
public class SoireeDejaLancee extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SoireeDejaLancee(EtatSoiree etat) {
		super("La Soirée est " + etat + ", elle ne peut plus être ouverte");
	}

}
