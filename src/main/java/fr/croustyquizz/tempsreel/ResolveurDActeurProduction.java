package fr.croustyquizz.tempsreel;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Refuse;

/** Refus fermé jusqu'à l'arrivée des accès Animateur et Joueur (#5, #10). */
@Component
@Profile("!dev & !recette")
public final class ResolveurDActeurProduction implements ResolveurDActeur {
	@Override
	public Acteur resoudre(HttpServletRequest requete) { throw refus(); }
	@Override
	public Acteur resoudre(SimpMessageHeaderAccessor connexion) { throw refus(); }
	private RuntimeException refus() {
		return new Refuse(401, "AUTHENTIFICATION_REQUISE", "Une authentification est requise.").exception();
	}
}
