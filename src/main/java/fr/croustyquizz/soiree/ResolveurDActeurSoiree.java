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

/** Résout la portée de l'Animateur et les accès portés par un Bearer. */
@Component
class ResolveurDActeurSoiree implements ResolveurDActeur {

	private static final String PREFIXE = "Bearer ";

	private final ServiceDesAcces acces;
	private final DepotDeSoirees soirees;
	private final ResolveurDActeurDuProfil profil;

	ResolveurDActeurSoiree(ServiceDesAcces acces, DepotDeSoirees soirees, ResolveurDActeurDuProfil profil) {
		this.acces = acces;
		this.soirees = soirees;
		this.profil = profil;
	}

	@Override
	public Acteur resoudre(HttpServletRequest requete) {
		if (ResolveurAnimateurConnecte.porteUnBearer(requete)) {
			return reconnaitre(requete.getHeader(HttpHeaders.AUTHORIZATION))
					.orElseGet(() -> {
						Acteur acteur = profil.resoudre(requete);
						return acteur.type() == Acteur.Type.ANIMATEUR
								? verifierDemonstration(acteur, soireeDeLaRoute(requete)) : acteur;
					});
		}
		var animateur = ResolveurAnimateurConnecte.resoudre(requete);
		UUID soireeId = soireeDeLaRoute(requete);
		if (soireeId == null) {
			throw soireeIntrouvable();
		}
		return resoudre(animateur, soireeId);
	}

	@Override
	public Acteur resoudre(SimpMessageHeaderAccessor connexion) {
		return reconnaitre(connexion.getFirstNativeHeader(HttpHeaders.AUTHORIZATION))
				.orElseGet(() -> verifierDemonstration(profil.resoudre(connexion), null));
	}

	@Override
	public Acteur resoudre(AnimateurConnecte animateur, UUID soireeId) {
		if (!soirees.existsByIdAndLieuId(soireeId, animateur.lieuId())) {
			throw soireeIntrouvable();
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
		return variables != null && variables.get("soireeId") instanceof String id ? UUID.fromString(id) : null;
	}

	private Acteur verifierDemonstration(Acteur acteur, UUID soireeDemandee) {
		if (acteur.type() == Acteur.Type.ANIMATEUR && (soirees.existsById(acteur.soireeId())
				|| soireeDemandee != null && !soireeDemandee.equals(acteur.soireeId()) && soirees.existsById(soireeDemandee))) {
			throw new Refuse(401, "AUTHENTIFICATION_REQUISE", "Une Soirée enregistrée exige une session Animateur.").exception();
		}
		return acteur;
	}

	private static RuntimeException soireeIntrouvable() {
		return new Refuse(404, "RESSOURCE_INTROUVABLE", "Soirée introuvable.").exception();
	}
}
