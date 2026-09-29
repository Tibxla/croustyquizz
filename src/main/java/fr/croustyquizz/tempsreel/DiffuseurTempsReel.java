package fr.croustyquizz.tempsreel;

import java.util.UUID;

/**
 * Seule porte de sortie vers les écrans. Un Mode de jeu publie des événements par
 * cette interface et n'écrit jamais directement dans une session WebSocket.
 */
public interface DiffuseurTempsReel {

	/** Toute la salle d'une Soirée : Écran de salle et Manettes. */
	void diffuserSalle(UUID soireeId, Evenement evenement);

	/** La Manette d'un seul Joueur. */
	void envoyerJoueur(UUID joueurId, Evenement evenement);

	/** La Console d'animation d'une Soirée. */
	void envoyerConsole(UUID soireeId, Evenement evenement);

}
