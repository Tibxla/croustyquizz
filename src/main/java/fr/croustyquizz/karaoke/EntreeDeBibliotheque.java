package fr.croustyquizz.karaoke;

import java.net.URI;

/**
 * Entrée validée du catalogue. `numero` et `lrc` n'existent que pour une Chanson, `tempoBpm` et
 * `departBeatMs` que pour une Instrumentale. `fichier` est relatif à la racine de la bibliothèque.
 */
public record EntreeDeBibliotheque(TypeDEntree type, String titre, String artiste, String album, long dureeMs,
        String licence, URI source, String fichier, long tailleOctets, Long numero, String lrc,
        Integer tempoBpm, Long departBeatMs) {
    /** Adresse publique du fichier, servi en statique sous /bibliotheque/. */
    public String url() { return "/bibliotheque/" + fichier; }
}
