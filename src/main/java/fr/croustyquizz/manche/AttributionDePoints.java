package fr.croustyquizz.manche;

import java.util.UUID;

/**
 * Points gagnés par un Concurrent (Joueur en Solo, Équipe en Format Équipe) pendant une Manche.
 *
 * @param concurrentId identifiant du Joueur ou de l'Équipe
 * @param valeur       nombre de points, positif
 * @param motif        raison lisible, par exemple « bonne réponse en 3 s »
 */
public record AttributionDePoints(UUID concurrentId, int valeur, String motif) {
}
