package fr.croustyquizz.manche;

/** Corps Problem Details commun ; aucun détail technique n'est exposé. */
public record Probleme(String type, String title, int status, String code, String detail, String instance) {
	public static Probleme de(Refuse refus, String route) {
		return new Probleme("about:blank", refus.code(), refus.statutHttp(), refus.code(), refus.detail(), route);
	}
}
