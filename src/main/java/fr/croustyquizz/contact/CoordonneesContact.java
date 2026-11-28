package fr.croustyquizz.contact;

import java.util.regex.Pattern;

import fr.croustyquizz.manche.Refuse;

record CoordonneesContact(String prenom, String nom, String numero) {
	private static final int LONGUEUR_MAX_NUMERO = 40;
	private static final Pattern MOBILES = Pattern.compile("(?:0|\\+33|0033)([67][0-9]{8})");
	private static final Pattern SEPARATEURS = Pattern.compile("[\\p{Zs}().-]");
	private static final Pattern CONTROLES = Pattern.compile("[\\p{Cc}\\p{Zl}\\p{Zp}]");

	static CoordonneesContact de(String prenom, String nom, String numero) {
		return new CoordonneesContact(nettoyerNom(prenom), nettoyerNom(nom), normaliserNumero(numero));
	}

	private static String nettoyerNom(String saisie) {
		String propre = saisie.strip();
		if (propre.isEmpty() || propre.codePointCount(0, propre.length()) > 120 || CONTROLES.matcher(propre).find()) {
			throw new Refuse(422, "DONNEES_INVALIDES", "Renseigne ton prénom et ton nom (120 caractères maximum).").exception();
		}
		return propre;
	}

	private static String normaliserNumero(String saisie) {
		if (saisie.length() <= LONGUEUR_MAX_NUMERO) {
			var mobile = MOBILES.matcher(SEPARATEURS.matcher(saisie).replaceAll(""));
			if (mobile.matches()) {
				return "+33" + mobile.group(1);
			}
		}
		throw new Refuse(422, "NUMERO_INVALIDE", "Saisis un numéro mobile français, commençant par 06, 07 ou +33.")
				.exception();
	}

	@Override
	public String toString() {
		return "CoordonneesContact[***]";
	}
}
