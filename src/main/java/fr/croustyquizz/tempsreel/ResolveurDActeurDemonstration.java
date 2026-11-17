package fr.croustyquizz.tempsreel;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Refuse;

/** Jetons de démonstration pour #80 : demo:TYPE:soireeId:acteurId, uniquement en dev/recette. */
@Component
@Profile({"dev", "recette"})
public final class ResolveurDActeurDemonstration implements ResolveurDActeur {
	private static final int NOMBRE_PARTIES = 3;
	@Override
	public Acteur resoudre(HttpServletRequest requete) { return lire(requete.getHeader("Authorization")); }
	@Override
	public Acteur resoudre(SimpMessageHeaderAccessor connexion) {
		return lire(connexion.getFirstNativeHeader("Authorization"));
	}
	private Acteur lire(String autorisation) {
		if (autorisation != null && autorisation.startsWith("Bearer demo:")) {
			String[] parties = autorisation.substring("Bearer demo:".length()).split(":", -1);
			if (parties.length == NOMBRE_PARTIES) {
				try { return new Acteur(Acteur.Type.valueOf(parties[0]), UUID.fromString(parties[2]), UUID.fromString(parties[1])); }
				catch (IllegalArgumentException invalide) {
					throw new Refuse(401, "AUTHENTIFICATION_REQUISE", "Le jeton de démonstration est invalide.").exception(invalide);
				}
			}
		}
		throw refus();
	}
	private RuntimeException refus() {
		return new Refuse(401, "AUTHENTIFICATION_REQUISE", "Un jeton de démonstration est requis.").exception();
	}
}
