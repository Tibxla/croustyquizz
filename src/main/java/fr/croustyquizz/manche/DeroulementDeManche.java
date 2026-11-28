package fr.croustyquizz.manche;

import java.util.List;

/**
 * Domaine Java pur d'une Manche, appelé sous le verrou de sa Soirée, minuteurs compris.
 * Un refus laisse l'instantané inchangé ; une fin interdit toute nouvelle commande.
 * Les vues et leur publication appartiennent au service, jamais au déroulement.
 */
public interface DeroulementDeManche {
	void demarrer();
	ResultatDeCommande recevoir(ActionJoueur action);
	ResultatDeCommande commander(CommandeDeManche commande);
	ResultatDeCommande mettreEnPause(CommandePilotage commande);
	ResultatDeCommande reprendre(CommandePilotage commande);
	ResultatDeCommande passerEtape(CommandePilotage commande);
	ResultatDeCommande terminer(CommandePilotage commande);
	/** Vérifie la cible du pilotage avant toute mutation ; les modes à étapes la redéfinissent. */
	default boolean viseEtape(java.util.UUID etapeId) { return etapeId == null; }
	/** Phase courante telle qu’elle figure dans la Console, ou {@code null} si le mode ne la contrôle pas. */
	default String phaseCourante() { return null; }
	/** Capture immuable à égalité par valeur, utilisée pour les vues, la persistance et les rappels de minuteur. */
	Object instantane();
	boolean estTerminee();
	/** Cumul des attributions acquises, publiables et restaurées avec l'instantané. */
	List<AttributionDePoints> points();
}
