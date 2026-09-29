package fr.croustyquizz.manche;

import java.time.Instant;
import java.util.UUID;

/**
 * Ce qu'un Joueur envoie depuis sa Manette pendant une Manche : Réponse, Buzz,
 * proposition de titre, inscription au Karaoké, « Je suis prêt », Vote…
 * Chaque Mode de jeu déclare ses propres actions et ignore celles qu'il ne connaît pas.
 */
public interface ActionJoueur {

	UUID joueurId();

	/** Instant de réception par le serveur, qui seul fait foi pour départager deux Joueurs. */
	Instant recueLe();

}
