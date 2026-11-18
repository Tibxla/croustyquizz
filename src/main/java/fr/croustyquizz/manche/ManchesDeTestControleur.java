package fr.croustyquizz.manche;

import java.util.Map;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import fr.croustyquizz.tempsreel.AccesAuxVues;

/** Création et démarrage temporaires sans Orchestrateur, réservés à dev et recette (#12). */
@RestController
@Profile({"dev", "recette"})
public class ManchesDeTestControleur {
	public record Creation(@NotNull ModeDeJeu mode, @NotNull Object reglages) {
		@com.fasterxml.jackson.annotation.JsonAnySetter
		public void refuserChampInconnu(String nom, Object valeur) { throw new IllegalArgumentException("Champ non prévu dans Creation."); }
	}
	private final RegistreServicesDeManche services;
	public ManchesDeTestControleur(RegistreServicesDeManche services) { this.services = services; }
	@PostMapping("/api/test/soirees/{soireeId}/manches")
	public ResponseEntity<?> creer(@PathVariable UUID soireeId, @Valid @RequestBody Creation creation, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		UUID id;
		try { id = services.pourMode(creation.mode()).creer(soireeId, creation.reglages()); }
		catch (IllegalArgumentException | tools.jackson.core.JacksonException invalide) {
			throw new Refuse(422, "DONNEES_INVALIDES", "Les réglages de Manche sont invalides.").exception(invalide);
		}
		return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(Map.of("mancheId", id, "etat", EtatManche.PRETE));
	}
	@PostMapping("/api/test/soirees/{soireeId}/manches/{mancheId}/demarrer")
	public ResponseEntity<?> demarrer(@PathVariable UUID soireeId, @PathVariable UUID mancheId,
			@RequestHeader("Idempotency-Key") UUID envoiId, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		String route = "/api/test/soirees/" + soireeId + "/manches/" + mancheId + "/demarrer";
		var resultat = services.pourManche(soireeId, mancheId).demarrer(mancheId, new EnvoiDeCommande(acteur, route, envoiId, Map.of()));
		return ReponsesDeCommande.http(resultat, route);
	}
}
