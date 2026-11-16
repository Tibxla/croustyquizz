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
	/** Capture immuable lue par le fournisseur du bloc et la persistance du mode. */
	Object instantane();
	boolean estTerminee();
	List<AttributionDePoints> points();
}
