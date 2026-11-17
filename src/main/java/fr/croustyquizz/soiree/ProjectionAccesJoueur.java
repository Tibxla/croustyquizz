package fr.croustyquizz.soiree;

import java.util.UUID;

/**
 * Réponse de l'arrivée d'un Joueur ({@code AccesJoueur} dans schemas.yaml). Le
 * jeton en clair ne sort qu'ici, vers la Manette qui vient de rejoindre.
 */
record ProjectionAccesJoueur(UUID soireeId, JoueurPublic joueur, String jetonReconnexion) {

	static ProjectionAccesJoueur de(ArriveeDeJoueur arrivee) {
		Joueur joueur = arrivee.joueur();
		// Le suivi de présence arrive avec #7 et #47 : un Joueur qui vient d'arriver est connecté.
		return new ProjectionAccesJoueur(arrivee.soiree().getId(),
				new JoueurPublic(joueur.getId(), joueur.getPseudo(), true), arrivee.jeton().valeur());
	}

	/** Le jeton ne doit jamais finir dans un journal. */
	@Override
	public String toString() {
		return "ProjectionAccesJoueur[soireeId=" + soireeId + ", joueur=" + joueur + ", jetonReconnexion=***]";
	}

	/** {@code JoueurPublic} dans schemas.yaml. */
	record JoueurPublic(UUID id, String pseudo, boolean connecte) {
	}

}
