package fr.croustyquizz.soiree;

/** Un Joueur de la Soirée porte déjà ce pseudo (code {@code PSEUDO_DEJA_PRIS} du contrat). */
public class PseudoDejaPris extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PseudoDejaPris() {
		super("Ce pseudo est déjà pris dans la Soirée");
	}

	/** Refus découvert par l'index de la base, lors de deux arrivées simultanées. */
	public PseudoDejaPris(Throwable cause) {
		super("Ce pseudo est déjà pris dans la Soirée", cause);
	}

}
