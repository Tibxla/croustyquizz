package fr.croustyquizz.soiree;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import fr.croustyquizz.manche.Refuse;

/**
 * Routes de Soirée du contrat docs/api/openapi.yaml : creerSoiree,
 * consulterSoiree, ouvrirSoiree et rejoindreSoiree. Toutes passent par
 * {@link ServiceSoiree} et répondent en {@code Cache-Control: no-store}.
 * <p>
 * Avant la connexion de l'Animateur (#5), les routes de la Console sont
 * ouvertes à tous, comme le prévoit #6 : ni session ni jeton CSRF ne sont
 * vérifiés. L'en-tête Idempotency-Key de l'ouverture est accepté sans être
 * stocké : rouvrir une Soirée déjà ouverte la rend déjà inchangée.
 */
@RestController
class SoireeControleur {

	private final ServiceSoiree service;

	SoireeControleur(ServiceSoiree service) {
		this.service = service;
	}

	@PostMapping("/api/soirees")
	ResponseEntity<ProjectionSoiree> creer(@RequestBody DemandeDeCreation demande) {
		if (demande.format() == null || demande.pilotage() == null) {
			throw new Refuse(400, "REQUETE_INVALIDE", "Le Format et le Pilotage sont obligatoires.").exception();
		}
		Soiree soiree = service.creer(demande.titre(), demande.format(), demande.pilotage());
		return ResponseEntity.created(URI.create("/api/soirees/" + soiree.getId()))
				.cacheControl(CacheControl.noStore())
				.body(ProjectionSoiree.de(soiree));
	}

	@GetMapping("/api/soirees/{soireeId}")
	ResponseEntity<ProjectionSoiree> consulter(@PathVariable UUID soireeId) {
		return ok(ProjectionSoiree.de(service.consulter(soireeId)));
	}

	@PostMapping("/api/soirees/{soireeId}/ouverture")
	ResponseEntity<ProjectionSoiree> ouvrir(@PathVariable UUID soireeId) {
		return ok(ProjectionSoiree.de(service.ouvrir(soireeId)));
	}

	@PostMapping("/api/soirees/rejoindre")
	ResponseEntity<ProjectionAccesJoueur> rejoindre(@RequestBody DemandeArrivee demande) {
		ArriveeDeJoueur arrivee = service.rejoindre(demande.codeAcces(), demande.pseudo());
		UUID soireeId = arrivee.soiree().getId();
		return ResponseEntity.created(URI.create("/api/soirees/" + soireeId + "/etat/manette"))
				.cacheControl(CacheControl.noStore())
				.body(ProjectionAccesJoueur.de(arrivee));
	}

	private static <T> ResponseEntity<T> ok(T corps) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corps);
	}

	/** Corps de creerSoiree ({@code CreationSoiree} dans schemas.yaml). */
	record DemandeDeCreation(String titre, Format format, Pilotage pilotage) {
	}

	/** Corps de rejoindreSoiree ({@code Rejoindre} dans schemas.yaml). */
	record DemandeArrivee(String codeAcces, String pseudo) {
	}

}
