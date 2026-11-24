package fr.croustyquizz.tempsreel;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import fr.croustyquizz.animateur.AnimateurConnecte;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Refuse;

/** Port d'identité HTTP et STOMP ; les contrôleurs ne manipulent que l'Acteur résolu. */
public interface ResolveurDActeur {
	Acteur resoudre(HttpServletRequest requete);
	Acteur resoudre(SimpMessageHeaderAccessor connexion);

	/** Résout le rôle Animateur pour la Soirée visée par un SUBSCRIBE de cette session. */
	default Acteur resoudre(AnimateurConnecte session, UUID soireeId) {
		throw new Refuse(404, "RESSOURCE_INTROUVABLE", "Soirée introuvable.").exception();
	}
}
