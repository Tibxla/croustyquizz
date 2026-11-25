package fr.croustyquizz.blindtest;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Réglages de consultation de l'API publique Deezer, sans clé.
 *
 * @param urlDeezer               racine de l'API, {@code https://api.deezer.com} en production
 * @param delaiDeezerMs           budget d'une consultation, transport et corps compris
 * @param tailleReponseMaxOctets  taille maximale d'une réponse acceptée
 */
@ConfigurationProperties("croustyquizz.blindtest")
public record ProprietesCatalogueMusical(URI urlDeezer, long delaiDeezerMs, long tailleReponseMaxOctets) {

	public ProprietesCatalogueMusical {
		if (urlDeezer == null || !urlDeezer.isAbsolute() || urlDeezer.getHost() == null
				|| !("http".equalsIgnoreCase(urlDeezer.getScheme()) || "https".equalsIgnoreCase(urlDeezer.getScheme()))
				|| urlDeezer.getRawQuery() != null || urlDeezer.getRawFragment() != null) {
			throw new IllegalArgumentException("URL Deezer HTTP absolue attendue, sans requête ni fragment.");
		}
		if (delaiDeezerMs <= 0 || tailleReponseMaxOctets <= 0) {
			throw new IllegalArgumentException("Réglages du catalogue musical invalides.");
		}
	}

}
