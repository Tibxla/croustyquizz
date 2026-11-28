package fr.croustyquizz.soiree;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.tempsreel.ResolveurDActeur;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Reconnaît les Joueurs à leur jeton de reconnexion et les Écrans de salle à
 * leur clé, en HTTP comme au CONNECT STOMP. Un secret opaque non reconnu au
 * CONNECT reçoit des consignes de reprise. Un Joueur exclu reçoit un motif
 * propre à son Exclusion. Le résolveur du profil traite les
 * autres en-têtes : jetons de démonstration en dev et recette, refus en
 * production. L'Animateur sera reconnu avec la connexion du Lieu (#5).
 *
 * Un Joueur ou un Écran de démonstration ne vise qu'une Soirée fictive : sur une
 * Soirée enregistrée, seuls le jeton de reconnexion et la clé d'Écran comptent.
 * Un Joueur de démonstration ne porte pas non plus l'UUID d'un vrai Joueur.
 * L'Animateur de démonstration reste accepté jusqu'à #5, pour les routes de test
 * des Manches (#106).
 */
@Component
@Primary
class ResolveurDActeurSoiree implements ResolveurDActeur {

	private static final String PREFIXE = "Bearer ";

	private final ServiceDesAcces acces;
	private final ObjectProvider<ResolveurDActeur> resolveurs;
	private final DepotDeSoirees soirees;
	private final DepotDeJoueurs joueurs;

	ResolveurDActeurSoiree(ServiceDesAcces acces, ObjectProvider<ResolveurDActeur> resolveurs, DepotDeSoirees soirees,
			DepotDeJoueurs joueurs) {
		this.acces = acces;
		this.resolveurs = resolveurs;
		this.soirees = soirees;
		this.joueurs = joueurs;
	}

	@Override
	public Acteur resoudre(HttpServletRequest requete) {
		return reconnaitre(extraireSecret(requete.getHeader("Authorization")))
				.orElseGet(() -> limiter(delegue().resoudre(requete)));
	}

	@Override
	public Acteur resoudre(SimpMessageHeaderAccessor connexion) {
		String secret = extraireSecret(connexion.getFirstNativeHeader("Authorization"));
		return reconnaitre(secret).orElseGet(() -> {
			if (Secrets.aLaForme(secret)) {
				if (joueurs.findByEmpreinteJeton(Secrets.empreinte(secret)).filter(Joueur::isExclu).isPresent()) {
					throw new Refuse(401, "AUTHENTIFICATION_REQUISE", "Tu ne fais plus partie de cette Soirée.").exception();
				}
				// Une clé d'Écran purgée et un jeton Joueur inconnu ont la même forme.
				throw new Refuse(401, "AUTHENTIFICATION_REQUISE", "Accès invalide ou expiré. "
						+ "Rouvre l'Écran de salle depuis la préparation de la Soirée pour obtenir une nouvelle clé, "
						+ "ou rejoins la Soirée avec son code si tu utilises la Manette.").exception();
			}
			return limiter(delegue().resoudre(connexion));
		});
	}

	private static String extraireSecret(String autorisation) {
		return autorisation != null && autorisation.startsWith(PREFIXE) ? autorisation.substring(PREFIXE.length()) : null;
	}

	private Optional<Acteur> reconnaitre(String secret) {
		return secret == null ? Optional.empty() : acces.reconnaitre(secret);
	}

	/**
	 * Le canal d'une Manette et sa dernière vue ne dépendent que de l'UUID du
	 * Joueur : un vrai Joueur est donc refusé même derrière une Soirée fictive.
	 */
	private Acteur limiter(Acteur acteur) {
		if (acteur.type() == Acteur.Type.ANIMATEUR) {
			return acteur;
		}
		if (soirees.existsById(acteur.soireeId())
				|| acteur.type() == Acteur.Type.JOUEUR && joueurs.existsById(acteur.identifiant())) {
			throw new Refuse(401, "AUTHENTIFICATION_REQUISE", acteur.type() == Acteur.Type.ECRAN
					? "Cette Soirée n'accepte pas d'Écran de démonstration : ouvre l'Écran de salle avec la clé créée depuis la Console."
					: "Cette Soirée n'accepte pas d'identité de démonstration : rejoins-la avec son code.").exception();
		}
		return acteur;
	}

	/**
	 * Le résolveur du profil, cherché à l'appel pour ne pas dépendre de soi-même à
	 * la création ; filtré par type, car Spring peut fournir un proxy.
	 */
	private ResolveurDActeur delegue() {
		return resolveurs.orderedStream().filter(resolveur -> !(resolveur instanceof ResolveurDActeurSoiree)).findFirst()
				.orElseThrow(() -> new IllegalStateException("Aucun résolveur d'Acteur pour ce profil"));
	}

}
