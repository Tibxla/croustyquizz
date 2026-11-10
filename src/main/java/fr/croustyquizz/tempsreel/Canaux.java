package fr.croustyquizz.tempsreel;

import java.util.UUID;

/** Destinations STOMP auxquelles les pages s'abonnent. */
public final class Canaux {

	private Canaux() {
	}

	/** Toute la salle d'une Soirée : Écran de salle et Manettes. */
	public static String salle(UUID soireeId) {
		return "/topic/soirees/" + soireeId + "/salle";
	}

	/** La Console d'animation d'une Soirée. */
	public static String console(UUID soireeId) {
		return "/topic/soirees/" + soireeId + "/console";
	}

	/** La Manette d'un seul Joueur. */
	public static String joueur(UUID joueurId) {
		return "/topic/joueurs/" + joueurId;
	}

}
