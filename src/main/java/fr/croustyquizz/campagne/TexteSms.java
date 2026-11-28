package fr.croustyquizz.campagne;

final class TexteSms {
	static final String LIEN_EXEMPLE = "https://croustyquizz.invalid/stop/exemple";
	private static final int MAX_SEGMENTS = 6;
	private static final String GSM = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ"
			+ " !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§"
			+ "¿abcdefghijklmnopqrstuvwxyzäöñüà";
	private static final String EXTENSION_GSM = "\f^{}\\[~]|€";

	private TexteSms() { }

	static String nettoyer(String texte) {
		String nettoye = texte.strip();
		if (nettoye.isEmpty()) {
			throw new IllegalArgumentException("Écris le texte du SMS.");
		}
		return nettoye;
	}

	static String preparer(String texte, String lien) {
		String complet = texte + "\nSTOP : " + lien;
		if (segments(complet) > MAX_SEGMENTS) {
			throw new IllegalArgumentException("Raccourcis le texte à six segments maximum, lien STOP compris.");
		}
		return complet;
	}

	static int segments(String texte) {
		boolean gsm = texte.codePoints().allMatch(caractere -> GSM.indexOf(caractere) >= 0
				|| EXTENSION_GSM.indexOf(caractere) >= 0);
		int unites = gsm ? texte.length() + (int) texte.chars().filter(c -> EXTENSION_GSM.indexOf(c) >= 0).count()
				: texte.length();
		if (unites <= (gsm ? 160 : 70)) {
			return 1;
		}
		int capacite = gsm ? 153 : 67;
		int segments = 1;
		int reste = capacite;
		var caracteres = texte.codePoints().iterator();
		while (caracteres.hasNext()) {
			int caractere = caracteres.nextInt();
			int longueur = Character.charCount(caractere);
			int poids = gsm && EXTENSION_GSM.indexOf(caractere) >= 0 ? 2 : longueur;
			if (poids > reste) {
				segments++;
				reste = capacite;
			}
			reste -= poids;
		}
		return segments;
	}
}
