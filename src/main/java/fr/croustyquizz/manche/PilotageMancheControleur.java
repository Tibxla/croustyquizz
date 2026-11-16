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
	public PilotageMancheControleur(RegistreServicesDeManche services) { this.services = services; }
	@PostMapping("/api/soirees/{soireeId}/manches/{mancheId}/commandes")
	public ResponseEntity<?> piloter(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
			@RequestHeader("Idempotency-Key") UUID envoiId, @Valid @RequestBody InterventionDeManche intervention, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		String route = "/api/soirees/" + soireeId + "/manches/" + mancheId + "/commandes";
		ResultatDeCommande resultat = services.pourManche(soireeId, mancheId)
				.piloter(mancheId, new EnvoiDeCommande(acteur, route, envoiId, intervention), intervention);
		return ReponsesDeCommande.http(resultat, route);
	}
}
