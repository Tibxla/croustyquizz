package fr.croustyquizz.manche;

/** Transporte un refus jusqu'à l'adaptateur HTTP commun. */
public final class RefusDeCommande extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private final Refuse resultatRefuse;
	public RefusDeCommande(Refuse refus) { super(refus.detail()); this.resultatRefuse = refus; }
	public RefusDeCommande(Refuse refus, Throwable cause) { super(refus.detail(), cause); this.resultatRefuse = refus; }
	public Refuse refus() { return resultatRefuse; }
}
