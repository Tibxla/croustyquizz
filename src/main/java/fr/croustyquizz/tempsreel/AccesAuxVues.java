package fr.croustyquizz.tempsreel;

import java.util.UUID;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Refuse;

/** Vérifie la portée et le rôle avant consultation ou commande. */
public final class AccesAuxVues {
	private AccesAuxVues() { }
	public static void verifier(Acteur acteur, UUID soireeId, Acteur.Type role) {
		if (!acteur.soireeId().equals(soireeId)) {
			throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Soirée introuvable.").exception();
		}
		if (role != null && acteur.type() != role) {
			throw new Refuse(403, "ACCES_REFUSE", "Cet accès ne permet pas cette opération.").exception();
		}
	}
	public static boolean autorise(Acteur acteur, String destination) {
		return Canaux.salle(acteur.soireeId()).equals(destination)
				|| (acteur.type() == Acteur.Type.ANIMATEUR && Canaux.console(acteur.soireeId()).equals(destination))
				|| (acteur.type() == Acteur.Type.JOUEUR && Canaux.joueur(acteur.identifiant()).equals(destination));
	}
}
