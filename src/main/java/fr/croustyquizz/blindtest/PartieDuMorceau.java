package fr.croustyquizz.blindtest;

/** Partie à trouver d'un Morceau, avec son barème (ADR 0009, point 8). */
public enum PartieDuMorceau {
	TITRE(70),
	ARTISTE(30);

	private final int bareme;

	PartieDuMorceau(int bareme) {
		this.bareme = bareme;
	}

	/** Points gagnés par le premier Joueur qui trouve cette partie. */
	public int points() {
		return bareme;
	}

	String attendue(Morceau morceau) {
		return this == TITRE ? morceau.titre() : morceau.artiste();
	}
}
