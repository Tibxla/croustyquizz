package fr.croustyquizz.manche;

/**
 * Pouvoir que l'Animateur peut confier à un Joueur par Délégation (#67). Liste fermée : jamais l'Exclusion,
 * l'Avertissement, la fin d'une Manche, le pilotage de la Soirée, le programme, les réglages des photos ni
 * l'invitation d'Animateurs.
 */
public enum Pouvoir {
	/** Placer un Passage, définir la priorité, appeler le Passage en tête et passer le courant (Karaoké). */
	FILE_ATTENTE,
	/** Couper ou relancer la Musique d'attente. */
	MUSIQUE_ATTENTE,
	/** Accepter ou refuser les photos en attente, et les voir pour en juger. */
	PHOTOS
}
