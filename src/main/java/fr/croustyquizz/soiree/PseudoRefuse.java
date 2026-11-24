package fr.croustyquizz.soiree;

/** Pseudo écarté par le {@link FiltreDePseudos} (code {@code PSEUDO_REFUSE} du contrat). */
public class PseudoRefuse extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PseudoRefuse() {
		super("Ce pseudo n'est pas accepté");
	}

}
