package fr.croustyquizz.tempsreel;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Refuse;

/** Captures des trois destinations, après identité et barrière d'abonnement du même acteur. */
@RestController
public class VuesControleur {
	private final DiffuseurStomp diffuseur;
	private final RegistreAbonnements abonnements;
	public VuesControleur(DiffuseurStomp diffuseur, RegistreAbonnements abonnements) {
		this.diffuseur = diffuseur; this.abonnements = abonnements;
	}
	@GetMapping("/api/soirees/{soireeId}/etat/salle")
	public ResponseEntity<VueSynchronisee> salle(@PathVariable UUID soireeId,
			@RequestParam(required = false) UUID abonnementId, Acteur acteur) {
		verifier(acteur, soireeId, null, abonnementId, Canaux.salle(soireeId));
		return reponse(diffuseur.capturerSalle(soireeId, abonnementId));
	}
	@GetMapping("/api/soirees/{soireeId}/etat/console")
	public ResponseEntity<VueSynchronisee> console(@PathVariable UUID soireeId,
			@RequestParam(required = false) UUID abonnementId, Acteur acteur) {
		verifier(acteur, soireeId, Acteur.Type.ANIMATEUR, abonnementId, Canaux.console(soireeId));
		return reponse(diffuseur.capturerConsole(soireeId, abonnementId));
	}
	@GetMapping("/api/soirees/{soireeId}/etat/manette")
	public ResponseEntity<VueSynchronisee> manette(@PathVariable UUID soireeId,
			@RequestParam(required = false) UUID abonnementId, Acteur acteur) {
		verifier(acteur, soireeId, Acteur.Type.JOUEUR, abonnementId, Canaux.joueur(acteur.identifiant()));
		return reponse(diffuseur.capturerJoueur(soireeId, acteur.identifiant(), abonnementId));
	}
	private void verifier(Acteur acteur, UUID soireeId, Acteur.Type role, UUID abonnement, String canal) {
		AccesAuxVues.verifier(acteur, soireeId, role);
		if (abonnement != null && !abonnements.estInstalle(abonnement, canal, acteur)) {
			throw new Refuse(409, "ABONNEMENT_INACTIF", "L'abonnement n'est pas installé pour cet acteur.").exception();
		}
	}
	private ResponseEntity<VueSynchronisee> reponse(VueSynchronisee vue) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(vue);
	}
}
