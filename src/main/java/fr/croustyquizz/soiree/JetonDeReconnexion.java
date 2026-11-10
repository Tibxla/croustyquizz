package fr.croustyquizz.soiree;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Secret remis au Joueur à son arrivée et conservé par sa Manette : c'est la
 * seule preuve de son identité. Le serveur n'en garde que l'empreinte.
 *
 * @param valeur 256 bits aléatoires en Base64 URL, sans remplissage
 */
public record JetonDeReconnexion(String valeur) {

	private static final int OCTETS = 32;

	public static JetonDeReconnexion generer(SecureRandom hasard) {
		byte[] octets = new byte[OCTETS];
		hasard.nextBytes(octets);
		return new JetonDeReconnexion(Base64.getUrlEncoder().withoutPadding().encodeToString(octets));
	}

	/** Empreinte SHA-256, la seule forme stockée en base. */
	public byte[] empreinte() {
		try {
			return MessageDigest.getInstance("SHA-256").digest(valeur.getBytes(StandardCharsets.US_ASCII));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 est toujours disponible dans le JDK", e);
		}
	}

	/** Le jeton ne doit jamais finir dans un journal. */
	@Override
	public String toString() {
		return "JetonDeReconnexion[***]";
	}

}
