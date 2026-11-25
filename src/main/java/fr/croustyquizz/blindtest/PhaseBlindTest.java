package fr.croustyquizz.blindtest;

/** Phase du Morceau courant (ADR 0009). */
public enum PhaseBlindTest {
	/** Le serveur demande le lien d'extrait, hors du verrou de la Soirée. */
	PREPARATION,
	/** L'extrait se joue ; le Buzz est ouvert aux Joueurs non bloqués. */
	EXTRAIT,
	/** Un Joueur a la main : l'extrait est en pause pendant sa Proposition de titre. */
	PROPOSITION,
	/** Titre, artiste et pochette sont montrés ; le Morceau est terminé. */
	REVELATION
}
