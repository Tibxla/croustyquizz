package fr.croustyquizz.blindtest;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Juge un champ de Proposition de titre (ADR 0009, point 7) en tolérant les écarts d'écriture. Saisie et réponse
 * attendue passent par la même normalisation : casse et accents ignorés, ponctuation changée en blanc, « & », « et »
 * et « and » équivalents, un article de tête retiré, puis espaces supprimés. Restent permises les fautes de frappe,
 * selon la longueur de la réponse : aucune jusqu'à 4 lettres, une de 5 à 8, deux au-delà.
 */
public final class CorrecteurTolerant {

	/** Articles retirés en tête seulement : « Let It Be » ne perd rien au milieu. */
	private static final Set<String> ARTICLES = Set.of("le", "la", "les", "l", "un", "une", "des", "the", "a", "an");
	/** Forme commune de « & », « et » et « and ». */
	private static final String LIAISON = "et";
	private static final Set<String> LIAISONS = Set.of("et", "and");
	private static final Pattern MARQUES = Pattern.compile(
			"[\\u0300-\\u036F\\u1AB0-\\u1AFF\\u1DC0-\\u1DFF\\u20D0-\\u20FF\\uFE00-\\uFE0F\\uFE20-\\uFE2F]+");
	/** Tout ce qui n'est ni lettre ni chiffre sépare deux mots : ponctuation, symboles et blancs Unicode. */
	private static final Pattern SEPARATEURS = Pattern.compile("[^\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);
	private static final int SANS_FAUTE_JUSQUA = 4;
	private static final int UNE_FAUTE_JUSQUA = 8;

	/** Vrai si la saisie, non vide une fois normalisée, est assez proche de la réponse attendue. */
	public boolean estJuste(String saisie, String attendu) {
		if (saisie == null || attendu == null) {
			return false;
		}
		Set<String> saisies = formes(mots(saisie));
		for (String reponse : formes(mots(attendu))) {
			int permises = fautesPermises(reponse.length());
			for (String forme : saisies) {
				if (distance(forme, reponse) <= permises) {
					return true;
				}
			}
		}
		return false;
	}

	/** Forme normalisée complète, sans retirer d'article : deux écritures du même titre ont la même clé. */
	static String cle(String texte) {
		return String.join("", mots(texte));
	}

	/** Fautes de frappe permises pour une réponse attendue de cette longueur, une fois normalisée. */
	static int fautesPermises(int longueur) {
		if (longueur <= SANS_FAUTE_JUSQUA) {
			return 0;
		}
		return longueur <= UNE_FAUTE_JUSQUA ? 1 : 2;
	}

	/**
	 * Distance de Damerau-Levenshtein restreinte, sur les points de code : un ajout, un retrait, un remplacement ou
	 * l'inversion de deux lettres voisines comptent chacun pour une faute.
	 */
	static int distance(String gauche, String droite) {
		int[] a = gauche.codePoints().toArray();
		int[] b = droite.codePoints().toArray();
		int[][] d = new int[a.length + 1][b.length + 1];
		for (int i = 0; i <= a.length; i++) {
			d[i][0] = i;
		}
		for (int j = 0; j <= b.length; j++) {
			d[0][j] = j;
		}
		for (int i = 1; i <= a.length; i++) {
			for (int j = 1; j <= b.length; j++) {
				int cout = a[i - 1] == b[j - 1] ? 0 : 1;
				d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cout);
				if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
					d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
				}
			}
		}
		return d[a.length][b.length];
	}

	/**
	 * Le texte entier sans espaces, et le même sans son article de tête s'il reste un autre mot qu'un article :
	 * « A-ha » donne « aha » et « ha », « The The » garde seulement « thethe ». Vide si le champ l'est.
	 */
	private static Set<String> formes(List<String> mots) {
		Set<String> formes = new LinkedHashSet<>();
		if (mots.isEmpty()) {
			return formes;
		}
		formes.add(String.join("", mots));
		List<String> suite = mots.subList(1, mots.size());
		if (ARTICLES.contains(mots.get(0)) && !ARTICLES.containsAll(suite)) {
			formes.add(String.join("", suite));
		}
		return formes;
	}

	private static List<String> mots(String texte) {
		String sansAccent = MARQUES.matcher(Normalizer.normalize(texte, Normalizer.Form.NFD)).replaceAll("");
		String minuscules = Normalizer.normalize(sansAccent, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
				.replace("œ", "oe").replace("æ", "ae").replace("ß", "ss").replace("&", " " + LIAISON + " ");
		List<String> mots = new ArrayList<>();
		for (String mot : Arrays.asList(SEPARATEURS.split(minuscules))) {
			if (!mot.isEmpty()) {
				mots.add(LIAISONS.contains(mot) ? LIAISON : mot);
			}
		}
		return mots;
	}

}
