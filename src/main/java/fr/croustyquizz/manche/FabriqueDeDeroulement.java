package fr.croustyquizz.manche;

/**
 * Point d'entrée d'un Mode de jeu pour le socle. Chaque mode en déclare une,
 * sous forme de bean Spring, et le socle choisit la bonne selon le {@link ModeDeJeu}
 * de la Manche à lancer.
 */
public interface FabriqueDeDeroulement {

	ModeDeJeu mode();

	DeroulementDeManche creer(ContexteDeManche contexte);

}
