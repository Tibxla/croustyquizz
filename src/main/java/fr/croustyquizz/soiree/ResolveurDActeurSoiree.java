package fr.croustyquizz.soiree;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import fr.croustyquizz.animateur.AnimateurConnecte;
import fr.croustyquizz.animateur.ResolveurAnimateurConnecte;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.tempsreel.ResolveurDActeur;
import fr.croustyquizz.tempsreel.ResolveurDActeurDuProfil;

/**
 * Résout la portée de l'Animateur et les accès portés par un Bearer.
 * <p>
 * Sans Bearer, l'Animateur est reconnu à sa session, pour une Soirée de son Lieu.
 * Un Bearer est d'abord un jeton de reconnexion de Joueur ou une clé d'Écran ;
 * sinon il est confié au résolveur du profil (démonstration en dev et recette,
 * refus en production). Une identité de démonstration ne vise qu'une Soirée
 * fictive : sur une Soirée enregistrée, l'Animateur passe par sa session, le
 * Joueur par son jeton de reconnexion et l'Écran par sa clé. Un Joueur de
 * démonstration ne porte pas non plus l'UUID d'un vrai Joueur.
 */
@Component
class ResolveurDActeurSoiree implements ResolveurDActeur {

	private static final String PREFIXE = "Bearer ";

	private final ServiceDesAcces acces;
	private final DepotDeSoirees soirees;
	private final DepotDeJoueurs joueurs;
	private final ResolveurDActeurDuProfil profil;

	ResolveurDActeurSoiree(ServiceDesAcces acces, DepotDeSoirees soirees, DepotDeJoueurs joueurs,
			ResolveurDActeurDuProfil profil) {
		this.acces = acces;
		this.soirees = soirees;
		this.joueurs = joueurs;
		this.profil = profil;
	}

	@Override
	public Acteur resoudre(HttpServletRequest requete) {
		if (ResolveurAnimateurConnecte.porteUnBearer(requete)) {
			return reconnaitre(requete.getHeader(HttpHeaders.AUTHORIZATION))
					.orElseGet(() -> limiter(profil.resoudre(requete), soireeDeLaRoute(requete)));
		}
		var animateur = ResolveurAnimateurConnecte.resoudre(requete);
		UUID soireeId = soireeDeLaRoute(requete);
		if (soireeId == null) {
			throw soireeIntrouvable().exception();
		}
		return resoudre(animateur, soireeId);
	}

	@Override
	public Acteur resoudre(SimpMessageHeaderAccessor connexion) {
		return reconnaitre(connexion.getFirstNativeHeader(HttpHeaders.AUTHORIZATION))
				.orElseGet(() -> limiter(profil.resoudre(connexion), null));
	}

	@Override
	public Acteur resoudre(AnimateurConnecte animateur, UUID soireeId) {
		if (!soirees.existsByIdAndLieuId(soireeId, animateur.lieuId())) {
			throw soireeIntrouvable().exception();
		}
		return new Acteur(Acteur.Type.ANIMATEUR, animateur.animateurId(), soireeId);
	}

	private Optional<Acteur> reconnaitre(String autorisation) {
		if (autorisation == null || !autorisation.startsWith(PREFIXE)) {
			return Optional.empty();
		}
		return acces.reconnaitre(autorisation.substring(PREFIXE.length()));
	}

	private static UUID soireeDeLaRoute(HttpServletRequest requete) {
		Map<?, ?> variables = (Map<?, ?>) requete.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		if (variables == null || !(variables.get("soireeId") instanceof String id)) {
			return null;
		}
		try {
			return UUID.fromString(id);
		} catch (IllegalArgumentException invalide) {
			throw soireeIntrouvable().exception(invalide);
		}
	}

	/**
	 * Réserve les identités de démonstration aux Soirées fictives. Le canal d'une
	 * Manette et sa dernière vue ne dépendent que de l'UUID du Joueur : un vrai
	 * Joueur est donc refusé même derrière une Soirée fictive.
	 */
	private Acteur limiter(Acteur acteur, UUID soireeDemandee) {
		if (acteur.type() == Acteur.Type.ANIMATEUR) {
			return verifierDemonstration(acteur, soireeDemandee);
		}
		if (soirees.existsById(acteur.soireeId())
				|| acteur.type() == Acteur.Type.JOUEUR && joueurs.existsById(acteur.identifiant())) {
			throw new Refuse(401, "AUTHENTIFICATION_REQUISE", acteur.type() == Acteur.Type.ECRAN
					? "Cette Soirée n'accepte pas d'Écran de démonstration : ouvre l'Écran de salle avec la clé créée depuis la Console."
					: "Cette Soirée n'accepte pas d'identité de démonstration : rejoins-la avec son code.").exception();
		}
		return acteur;
	}

	private Acteur verifierDemonstration(Acteur acteur, UUID soireeDemandee) {
		if (soirees.existsById(acteur.soireeId())
				|| soireeDemandee != null && !soireeDemandee.equals(acteur.soireeId()) && soirees.existsById(soireeDemandee)) {
			throw new Refuse(401, "AUTHENTIFICATION_REQUISE", "Une Soirée enregistrée exige une session Animateur.").exception();
		}
		return acteur;
	}

	private static Refuse soireeIntrouvable() {
		return new Refuse(404, "RESSOURCE_INTROUVABLE", "Soirée introuvable.");
	}
}
