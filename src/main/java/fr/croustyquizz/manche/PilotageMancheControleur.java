package fr.croustyquizz.manche;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import fr.croustyquizz.tempsreel.AccesAuxVues;

/** Adapte piloterManche au service du mode ; identité et accusé restent ceux du serveur. */
@RestController
public class PilotageMancheControleur {
	private final RegistreServicesDeManche services;
	private final EtatDeSoiree soirees;
	private final SocleDeManche socle;

	public PilotageMancheControleur(RegistreServicesDeManche services, EtatDeSoiree soirees, SocleDeManche socle) {
		this.services = services;
		this.soirees = soirees;
		this.socle = socle;
	}
	@PostMapping("/api/soirees/{soireeId}/manches/{mancheId}/commandes")
	public ResponseEntity<?> piloter(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
			@RequestHeader("Idempotency-Key") UUID envoiId, @Valid @RequestBody InterventionDeManche intervention, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		String route = "/api/soirees/" + soireeId + "/manches/" + mancheId + "/commandes";
		var envoi = new EnvoiDeCommande(acteur, route, envoiId, intervention);
		ResultatDeCommande resultat = socle.verrous().executer(soireeId, () -> {
			var service = services.pourManche(soireeId, mancheId);
			if (soirees.estEnCours(soireeId)) {
				return service.piloter(mancheId, envoi, intervention);
			}
			return service.refuserPilotage(mancheId, envoi, new Refuse(409, "ETAT_INCOMPATIBLE", "La Soirée doit être en cours."));
		});
		return ReponsesDeCommande.http(resultat, route);
	}
}
