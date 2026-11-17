package fr.croustyquizz.karaoke;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Réglages de consultation de la source de paroles. */
@ConfigurationProperties("croustyquizz.karaoke")
public record ProprietesSourceKaraoke(URI urlLrclib, String userAgentLrclib, long delaiSourceMs,
        long tailleReponseSourceMaxOctets, int longueurRechercheMax) {
    public ProprietesSourceKaraoke {
        if (urlLrclib == null || !urlLrclib.isAbsolute() || urlLrclib.getHost() == null
                || !("http".equalsIgnoreCase(urlLrclib.getScheme()) || "https".equalsIgnoreCase(urlLrclib.getScheme()))
                || urlLrclib.getRawQuery() != null || urlLrclib.getRawFragment() != null) {
            throw new IllegalArgumentException("URL de source HTTP absolue attendue, sans requête ni fragment");
        }
        if (userAgentLrclib == null || userAgentLrclib.isBlank() || delaiSourceMs <= 0
                || tailleReponseSourceMaxOctets <= 0 || longueurRechercheMax < 1 || longueurRechercheMax > 120) {
            throw new IllegalArgumentException("Réglages de source Karaoké invalides");
        }
    }
}
