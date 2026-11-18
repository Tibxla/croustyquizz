package fr.croustyquizz.soiree;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.tempsreel.ResolveurDActeur;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Reconnaît les Joueurs à leur jeton de reconnexion et les Écrans de salle à
 * leur clé, en HTTP comme au CONNECT STOMP. Tout autre en-tête est confié au
 * résolveur du profil : jetons de démonstration en dev et recette, refus en
 * production. L'Animateur sera reconnu avec la connexion du Lieu (#5).
 */
@Component
@Primary
class ResolveurDActeurSoiree implements ResolveurDActeur {

	private static final String PREFIXE = "Bearer ";

	private final ServiceDesAcces acces;
	private final ObjectProvider<ResolveurDActeur> resolveurs;

	ResolveurDActeurSoiree(ServiceDesAcces acces, ObjectProvider<ResolveurDActeur> resolveurs) {
		this.acces = acces;
		this.resolveurs = resolveurs;
	}

	@Override
	public Acteur resoudre(HttpServletRequest requete) {
		return reconnaitre(requete.getHeader("Authorization")).orElseGet(() -> delegue().resoudre(requete));
	}

	@Override
	public Acteur resoudre(SimpMessageHeaderAccessor connexion) {
		return reconnaitre(connexion.getFirstNativeHeader("Authorization"))
				.orElseGet(() -> delegue().resoudre(connexion));
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
