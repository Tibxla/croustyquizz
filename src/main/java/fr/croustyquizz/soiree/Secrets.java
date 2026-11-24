package fr.croustyquizz.soiree;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Secrets opaques de 256 bits en Base64 URL. Le serveur n'en garde que
 * l'empreinte SHA-256.
 */
public final class Secrets {

	private static final int OCTETS = 32;

	/** Forme d'un secret compatible avec les capacités existantes. */
	private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{43,128}");

	private Secrets() {
	}

	public static String tirer(SecureRandom hasard) {
		byte[] octets = new byte[OCTETS];
		hasard.nextBytes(octets);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
	}

	public static byte[] empreinte(String secret) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.US_ASCII));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 est toujours disponible dans le JDK", e);
		}
	}

	/** Vrai si la chaîne peut être un secret : évite une requête pour une valeur fantaisiste. */
	public static boolean aLaForme(String candidat) {
		return candidat != null && FORMAT.matcher(candidat).matches();
	}

}
