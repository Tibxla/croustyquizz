package fr.croustyquizz.blindtest;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Juge un champ de Proposition de titre (ADR 0009, point 9) : juste s'il égale la réponse attendue sans tenir compte
 * de la casse, des accents ni des blancs répétés. Même règle que la clé de pseudo de {@code soiree}, recopiée ici
 * pour ne pas en dépendre. #18 le remplacera par un correcteur tolérant aux articles et aux fautes de frappe.
 */
public final class CorrecteurExact {

	private static final Pattern MARQUES = Pattern.compile(
			"[\\u0300-\\u036F\\u1AB0-\\u1AFF\\u1DC0-\\u1DFF\\u20D0-\\u20FF\\uFE00-\\uFE0F\\uFE20-\\uFE2F]+");
	/** Tous les blancs Unicode, espaces insécables compris, que {@code \s} seul et {@code strip()} ignorent. */
	private static final Pattern BLANCS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

	/** Vrai si la saisie, non vide une fois normalisée, égale la réponse attendue normalisée. */
	public boolean estJuste(String saisie, String attendu) {
		if (saisie == null || attendu == null) {
			return false;
		}
		String cle = normaliser(saisie);
		return !cle.isEmpty() && cle.equals(normaliser(attendu));
	}

	static String normaliser(String texte) {
		String sansAccent = MARQUES.matcher(Normalizer.normalize(texte, Normalizer.Form.NFD)).replaceAll("");
		String recompose = Normalizer.normalize(sansAccent, Normalizer.Form.NFC);
		return BLANCS.matcher(recompose).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
	}

}
