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
 * leur clé, en HTTP comme au CONNECT STOMP. Tout autre en-tête est confié au
 * résolveur du profil : jetons de démonstration en dev et recette, refus en
 * production. En démonstration, seul l'Animateur accède à une Soirée enregistrée,
 * jusqu'à la connexion du Lieu (#5).
 */
@Component
@Primary
class ResolveurDActeurSoiree implements ResolveurDActeur {

	private static final String PREFIXE = "Bearer ";

	private final ServiceDesAcces acces;
	private final DepotDeSoirees soirees;
	private final ObjectProvider<ResolveurDActeur> resolveurs;

	ResolveurDActeurSoiree(ServiceDesAcces acces, DepotDeSoirees soirees, ObjectProvider<ResolveurDActeur> resolveurs) {
		this.acces = acces;
		this.soirees = soirees;
		this.resolveurs = resolveurs;
	}

	@Override
	public Acteur resoudre(HttpServletRequest requete) {
		String autorisation = requete.getHeader("Authorization");
		return reconnaitre(autorisation).orElseGet(() -> verifierDemonstration(delegue().resoudre(requete), autorisation));
	}

	@Override
	public Acteur resoudre(SimpMessageHeaderAccessor connexion) {
		String autorisation = connexion.getFirstNativeHeader("Authorization");
		return reconnaitre(autorisation)
				.orElseGet(() -> verifierDemonstration(delegue().resoudre(connexion), autorisation));
	}

	private Acteur verifierDemonstration(Acteur acteur, String autorisation) {
		if (acteur.type() != Acteur.Type.ANIMATEUR && autorisation != null && autorisation.startsWith(PREFIXE + "demo:")
				&& soirees.existsById(acteur.soireeId())) {
			throw new Refuse(401, "AUTHENTIFICATION_REQUISE", "Utilise l’accès de cette Soirée.").exception();
		}
		return acteur;
	}

	private Optional<Acteur> reconnaitre(String autorisation) {
		if (autorisation == null || !autorisation.startsWith(PREFIXE)) {
			return Optional.empty();
		}
		return acces.reconnaitre(autorisation.substring(PREFIXE.length()));
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
