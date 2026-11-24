package fr.croustyquizz.tempsreel;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Refuse;

/** Refuse les identités que le résolveur de Soirée n’a pas reconnues. */
@Component
@Fallback
@Profile("!dev & !recette")
public final class ResolveurDActeurProduction implements ResolveurDActeurDuProfil {
	@Override
	public Acteur resoudre(HttpServletRequest requete) { throw refus(); }
	@Override
	public Acteur resoudre(SimpMessageHeaderAccessor connexion) { throw refus(); }
	private RuntimeException refus() {
		return new Refuse(401, "AUTHENTIFICATION_REQUISE", "Une authentification est requise.").exception();
	}
}
