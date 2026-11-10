package fr.croustyquizz.soiree;

import java.util.Locale;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Code d'accès d'une Soirée : six caractères faciles à lire sur un écran et à
 * taper sur un téléphone, sans 0, O, 1 ni I qu'on confond.
 */
public final class CodeAcces {

	static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	static final int LONGUEUR = 6;
	private static final Pattern FORMAT = Pattern.compile("[A-HJ-NP-Z2-9]{6}");

	private CodeAcces() {
	}

	/** Tire un code au hasard. Son unicité est vérifiée par l'appelant. */
	public static String generer(RandomGenerator hasard) {
		StringBuilder code = new StringBuilder(LONGUEUR);
		for (int i = 0; i < LONGUEUR; i++) {
			code.append(ALPHABET.charAt(hasard.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

	/**
	 * Remet en forme un code tapé à la main : espaces retirés, majuscules.
	 * Vide si le résultat ne peut pas être un code d'accès.
	 */
	public static Optional<String> normaliser(String saisie) {
		if (saisie == null) {
			return Optional.empty();
		}
		String code = saisie.replaceAll("\s", "").toUpperCase(Locale.ROOT);
		return estValide(code) ? Optional.of(code) : Optional.empty();
	}

	static boolean estValide(String code) {
		return code != null && FORMAT.matcher(code).matches();
	}

}
