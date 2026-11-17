package fr.croustyquizz.karaoke;



/** Résumé LRCLIB ; une durée absente ou non positive reste inconnue. */
public record Chanson(String chansonId, long idLrclib, String titre,
        String artiste, String album, Long dureeMs, EtatParoles etatParoles) {}
