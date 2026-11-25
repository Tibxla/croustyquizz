package fr.croustyquizz.soiree;

/**
 * Résultat de l'arrivée d'un Joueur. Le jeton n'existe en clair qu'ici : il est
 * remis à la Manette, puis seule son empreinte reste en base.
 */
public record ArriveeDeJoueur(Joueur joueur, JetonDeReconnexion jeton, Soiree soiree) {
}
