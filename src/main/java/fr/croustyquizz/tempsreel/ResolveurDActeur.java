package fr.croustyquizz.tempsreel;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import fr.croustyquizz.manche.Acteur;

/** Port d'identité HTTP et STOMP ; les contrôleurs ne manipulent que l'Acteur résolu. */
public interface ResolveurDActeur {
	Acteur resoudre(HttpServletRequest requete);
	Acteur resoudre(SimpMessageHeaderAccessor connexion);
}
