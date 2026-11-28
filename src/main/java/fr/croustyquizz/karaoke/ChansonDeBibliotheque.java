package fr.croustyquizz.karaoke;

import java.net.URI;

/** Fichier et crédit d'une Chanson de la Bibliothèque de base, publics dans toutes les vues. */
public record ChansonDeBibliotheque(String fichier, long tailleOctets, String licence, URI urlLicence, URI urlSource) { }
