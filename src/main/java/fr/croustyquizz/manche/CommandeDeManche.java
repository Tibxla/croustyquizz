package fr.croustyquizz.manche;

import java.time.Instant;

/** Commande propre à un mode, identifiée et horodatée par le serveur. */
public interface CommandeDeManche {
	Acteur acteur();
	Instant recueLe();
}
