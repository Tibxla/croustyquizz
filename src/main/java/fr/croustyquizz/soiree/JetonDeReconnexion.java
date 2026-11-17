package fr.croustyquizz.soiree;

import java.security.SecureRandom;

/**
 * Secret remis au Joueur à son arrivée et conservé par sa Manette : c'est la
 * seule preuve de son identité. Le serveur n'en garde que l'empreinte.
 *
 * @param valeur 256 bits aléatoires en Base64 URL, sans remplissage
 */
public record JetonDeReconnexion(String valeur) {

	public static JetonDeReconnexion generer(SecureRandom hasard) {
		return new JetonDeReconnexion(Secrets.tirer(hasard));
	}

	/** Empreinte SHA-256, la seule forme stockée en base. */
	public byte[] empreinte() {
		return Secrets.empreinte(valeur);
	}

	/** Le jeton ne doit jamais finir dans un journal. */
	@Override
	public String toString() {
		return "JetonDeReconnexion[***]";
	}

}
