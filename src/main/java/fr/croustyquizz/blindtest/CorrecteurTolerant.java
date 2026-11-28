package fr.croustyquizz.blindtest;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Juge un champ de Proposition de titre (ADR 0009, point 7) en tolérant les écarts d'écriture. Saisie et réponse
 * attendue passent par la même normalisation : casse et accents ignorés, ponctuation changée en blanc, « & », « et »
 * et « and » équivalents, un article de tête retiré, puis espaces supprimés. Restent permises les fautes de frappe,
 * selon la longueur de la réponse sans son article : aucune jusqu'à 4 lettres, une de 5 à 8, deux au-delà.
 */
public final class CorrecteurTolerant {

	/** Articles retirés en tête seulement : « Let It Be » ne perd rien au milieu. « l » ne l'est que suivi de « ' ». */
	private static final Set<String> ARTICLES = Set.of("le", "la", "les", "un", "une", "des", "the", "a", "an");
	private static final String ELIDE = "l";
	private static final Pattern ELISION = Pattern.compile("^l['’ʼ]");
	/** Forme commune de « & », « et » et « and », puis ses écritures acceptées collées aux mots voisins. */
	private static final String LIAISON = "et";
	private static final Set<String> LIAISONS = Set.of("et", "and");
	private static final List<String> ECRITURES_LIAISON = List.of("et", "and");
	/** Au-delà, les liaisons suivantes gardent leur forme commune : le nombre de variantes reste borné. */
	private static final int LIAISONS_VARIEES_MAX = 4;
	private static final Pattern MARQUES = Pattern.compile(
			"[\\u0300-\\u036F\\u1AB0-\\u1AFF\\u1DC0-\\u1DFF\\u20D0-\\u20FF\\uFE00-\\uFE0F\\uFE20-\\uFE2F]+");
	/**
	 * Tout ce qui n'est ni lettre, ni marque, ni chiffre sépare deux mots : ponctuation, symboles et blancs Unicode.
	 * Les signes vocaliques (« कला ») sont des marques : ils restent dans le mot.
	 */
	private static final Pattern SEPARATEURS = Pattern.compile("[^\\p{L}\\p{M}\\p{N}]+",
			Pattern.UNICODE_CHARACTER_CLASS);
	private static final Pattern BLANCS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
	private static final int SANS_FAUTE_JUSQUA = 4;
	private static final int UNE_FAUTE_JUSQUA = 8;

	/** Vrai si la saisie, non vide une fois normalisée, est assez proche de la réponse attendue. */
	public boolean estJuste(String saisie, String attendu) {
		if (saisie == null || attendu == null) {
			return false;
		}
		Texte reponse = Texte.lire(attendu);
		if (reponse.mots().isEmpty()) {
			// Nom fait de ponctuation (« !!! ») : seule la saisie exacte, non vide, est juste.
			String cle = simple(saisie);
			return !cle.isEmpty() && cle.equals(simple(attendu));
		}
		Texte proposee = Texte.lire(saisie);
		if (proposee.mots().isEmpty()) {
			return false;
		}
		// L'article de tête ne compte pas dans la longueur : « The Pure » n'a pas de faute permise pour « The Cure ».
		int permises = reponse.formes().stream().mapToInt(forme -> fautesPermises(longueur(compacte(forme))))
				.min().orElse(0);
		Set<String> attendues = variantes(reponse);
		for (String forme : variantes(proposee)) {
			for (String exacte : attendues) {
				if (distance(forme, exacte) <= permises) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Formes que la correction accepte sans faute de frappe : texte entier et texte sans article de tête, avec chaque
	 * écriture des liaisons. Deux textes qui en partagent une ne se distinguent pas : le tirage y voit un doublon.
	 */
	static Set<String> formesExactes(String texte) {
		Texte lu = Texte.lire(texte);
		return lu.mots().isEmpty() ? Set.of(simple(texte)) : variantes(lu);
	}

	/** Fautes de frappe permises pour une réponse attendue de cette longueur, en points de code. */
	static int fautesPermises(int longueur) {
		if (longueur <= SANS_FAUTE_JUSQUA) {
			return 0;
		}
		return longueur <= UNE_FAUTE_JUSQUA ? 1 : 2;
	}

	/**
	 * Distance de Damerau-Levenshtein, sur les points de code : un ajout, un retrait, un remplacement ou l'inversion de
	 * deux lettres comptent chacun pour une faute, même si l'on écrit ensuite entre les lettres inversées
	 * (« CA » devient « ABC » en deux fautes).
	 */
	static int distance(String gauche, String droite) {
		int[] a = gauche.codePoints().toArray();
		int[] b = droite.codePoints().toArray();
		int infini = a.length + b.length;
		int[][] d = new int[a.length + 2][b.length + 2];
		d[0][0] = infini;
		for (int i = 0; i <= a.length; i++) {
			d[i + 1][0] = infini;
			d[i + 1][1] = i;
		}
		for (int j = 0; j <= b.length; j++) {
			d[0][j + 1] = infini;
			d[1][j + 1] = j;
		}
		Map<Integer, Integer> derniereLigne = new HashMap<>();
		for (int i = 1; i <= a.length; i++) {
			int derniereColonne = 0;
			for (int j = 1; j <= b.length; j++) {
				int ligne = derniereLigne.getOrDefault(b[j - 1], 0);
				int colonne = derniereColonne;
				int cout = 1;
				if (a[i - 1] == b[j - 1]) {
					cout = 0;
					derniereColonne = j;
				}
				int remplacement = d[i][j] + cout;
				int ajout = d[i + 1][j] + 1;
				int retrait = d[i][j + 1] + 1;
				int inversion = d[ligne][colonne] + (i - ligne - 1) + 1 + (j - colonne - 1);
				d[i + 1][j + 1] = Math.min(Math.min(remplacement, ajout), Math.min(retrait, inversion));
			}
			derniereLigne.put(a[i - 1], i);
		}
		return d[a.length + 1][b.length + 1];
	}

	private static int longueur(String texte) {
		return texte.codePointCount(0, texte.length());
	}

	private static String compacte(List<String> mots) {
		return String.join("", mots);
	}

	/** Chaque forme du texte, sans espaces, avec « et » ou « and » à la place de chaque liaison. */
	private static Set<String> variantes(Texte texte) {
		Set<String> variantes = new LinkedHashSet<>();
		for (List<String> forme : texte.formes()) {
			List<String> partielles = List.of("");
			int liaisons = 0;
			for (String mot : forme) {
				List<String> ecritures = List.of(mot);
				if (LIAISON.equals(mot) && liaisons < LIAISONS_VARIEES_MAX) {
					ecritures = ECRITURES_LIAISON;
					liaisons++;
				}
				List<String> suivantes = new ArrayList<>();
				for (String debut : partielles) {
					for (String ecriture : ecritures) {
						suivantes.add(debut + ecriture);
					}
				}
				partielles = suivantes;
			}
			variantes.addAll(partielles);
		}
		return variantes;
	}

	/** Normalisation de #17 : casse, accents et blancs, sans rien retirer d'autre. */
	private static String simple(String texte) {
		return BLANCS.matcher(sansAccent(texte)).replaceAll(" ").strip();
	}

	private static String sansAccent(String texte) {
		String decompose = MARQUES.matcher(Normalizer.normalize(texte, Normalizer.Form.NFD)).replaceAll("");
		return Normalizer.normalize(decompose, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
	}

	/**
	 * Mots normalisés d'un texte ; {@code elision} vaut vrai si le premier mot était « l' », seul « l » article.
	 */
	private record Texte(List<String> mots, boolean elision) {

		static Texte lire(String texte) {
			String minuscules = sansAccent(texte).strip()
					.replace("œ", "oe").replace("æ", "ae").replace("ß", "ss").replace("&", " " + LIAISON + " ");
			List<String> mots = new ArrayList<>();
			for (String mot : SEPARATEURS.split(minuscules)) {
				if (!mot.isEmpty()) {
					mots.add(LIAISONS.contains(mot) ? LIAISON : mot);
				}
			}
			return new Texte(List.copyOf(mots), ELISION.matcher(minuscules).find());
		}

		/**
		 * Le texte entier, et le même sans son article de tête s'il reste un autre mot qu'un article : « A-ha » donne
		 * « a ha » et « ha », « The The » garde seulement « the the ». Vide si le texte n'a aucun mot.
		 */
		List<List<String>> formes() {
			if (mots.isEmpty()) {
				return List.of();
			}
			String premier = mots.getFirst();
			boolean article = ARTICLES.contains(premier) || elision && ELIDE.equals(premier);
			List<String> suite = mots.subList(1, mots.size());
			if (article && !ARTICLES.containsAll(suite)) {
				return List.of(mots, suite);
			}
			return List.of(mots);
		}

	}

}
