package fr.croustyquizz.animateur;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

import fr.croustyquizz.manche.Refuse;

public final class ResolveurAnimateurConnecte {
	private ResolveurAnimateurConnecte() { }

	public static boolean porteUnBearer(HttpServletRequest requete) {
		String autorisation = requete.getHeader(HttpHeaders.AUTHORIZATION);
		return autorisation != null && autorisation.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length());
	}

	public static AnimateurConnecte resoudre(HttpServletRequest requete) {
		var session = SessionAnimateur.de(requete.getSession(false));
		if (session == null || porteUnBearer(requete)) {
			throw new Refuse(401, "AUTHENTIFICATION_REQUISE", "Connectez-vous pour accéder à la Soirée.").exception();
		}
		return session.identite();
	}
}
