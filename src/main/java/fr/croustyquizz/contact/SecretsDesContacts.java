package fr.croustyquizz.contact;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Capacités signées et empreintes d’opposition, avec deux usages distincts de la clé. */
final class SecretsDesContacts {
	private static final String ALGORITHME = "HmacSHA256";
	private static final Pattern FORME_DU_LIEN = Pattern.compile(
			"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.[A-Za-z0-9_-]{43}");
	private static final int LONGUEUR_UUID = 36;

	private final SecretKeySpec cle;

	SecretsDesContacts(byte[] octets) {
		this.cle = new SecretKeySpec(octets, ALGORITHME);
	}

	String fragment(UUID id) {
		return "#cle=" + id + "." + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(signer("lien/" + id));
	}

	Optional<UUID> verifier(String capacite) {
		if (!FORME_DU_LIEN.matcher(capacite).matches()) {
			return Optional.empty();
		}
		UUID id = UUID.fromString(capacite.substring(0, LONGUEUR_UUID));
		String attendue = fragment(id).substring("#cle=".length());
		return MessageDigest.isEqual(capacite.getBytes(StandardCharsets.US_ASCII),
				attendue.getBytes(StandardCharsets.US_ASCII)) ? Optional.of(id) : Optional.empty();
	}

	byte[] empreinte(UUID lieuId, String numero) {
		return signer("opposition/" + lieuId + "/" + numero);
	}

	private byte[] signer(String message) {
		try {
			Mac mac = Mac.getInstance(ALGORITHME);
			mac.init(cle);
			return mac.doFinal(message.getBytes(StandardCharsets.US_ASCII));
		} catch (GeneralSecurityException erreur) {
			throw new IllegalStateException("HmacSHA256 est fourni par le JDK.", erreur);
		}
	}
}
