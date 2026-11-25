package fr.croustyquizz.soiree;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.ReponsesDeCommande;
import fr.croustyquizz.tempsreel.AccesAuxVues;

@RestController
class PilotageSoireeControleur {
	private final OrchestrateurDeSoiree orchestrateur;

	PilotageSoireeControleur(OrchestrateurDeSoiree orchestrateur) {
		this.orchestrateur = orchestrateur;
	}

	@PostMapping("/api/soirees/{soireeId}/commandes")
	ResponseEntity<?> piloter(@PathVariable UUID soireeId, @RequestHeader("Idempotency-Key") UUID envoiId,
			@Valid @RequestBody CommandeSoiree commande, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		String route = "/api/soirees/" + soireeId + "/commandes";
		return ReponsesDeCommande.http(orchestrateur.piloter(acteur, route, envoiId, commande), route);
	}
}
