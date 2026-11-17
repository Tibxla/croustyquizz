package fr.croustyquizz.manche;

/** Refus métier sans mutation ; code et détail sont ceux du contrat Probleme. */
public record Refuse(int statutHttp, String code, String detail) implements ResultatDeCommande, java.io.Serializable {
	private static final long serialVersionUID = 1L;
	public RefusDeCommande exception(Throwable cause) { return new RefusDeCommande(this, cause); }
	public RefusDeCommande exception() { return new RefusDeCommande(this); }
}
