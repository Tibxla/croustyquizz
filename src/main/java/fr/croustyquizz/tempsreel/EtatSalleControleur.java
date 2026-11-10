package fr.croustyquizz.tempsreel;

import java.net.URI;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Capture du prototype après vérification de l'installation de son abonnement. */
@RestController
@Profile({"dev", "recette"})
class EtatSalleControleur {
	private final DiffuseurStomp diffuseur;
	private final RegistreAbonnements abonnements;

	EtatSalleControleur(DiffuseurStomp diffuseur, RegistreAbonnements abonnements) {
		this.diffuseur = diffuseur;
		this.abonnements = abonnements;
	}

	@GetMapping("/api/test/soirees/{soireeId}/etat/salle")
	ResponseEntity<?> capturer(@PathVariable UUID soireeId, @RequestParam(required = false) UUID abonnementId) {
		if (abonnementId != null && !abonnements.estInstalle(abonnementId, Canaux.salle(soireeId))) {
			ProblemDetail probleme = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
					"L'abonnement n'est pas installé sur cette destination.");
			probleme.setTitle("Abonnement inactif");
			probleme.setProperty("code", "ABONNEMENT_INACTIF");
			probleme.setInstance(URI.create("/api/test/soirees/" + soireeId + "/etat/salle"));
			return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore()).body(probleme);
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(diffuseur.capturerSalle(soireeId, abonnementId));
	}
}
