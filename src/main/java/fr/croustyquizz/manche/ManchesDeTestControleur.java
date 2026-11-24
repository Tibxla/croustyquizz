package fr.croustyquizz.manche;

import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import fr.croustyquizz.tempsreel.AccesAuxVues;

/** Création et démarrage sans Orchestrateur (#12) et choix du Pilotage, réservés à dev et recette. */
@RestController
@Profile({"dev", "recette"})
public class ManchesDeTestControleur {
	public record Creation(@NotNull ModeDeJeu mode, @NotNull Object reglages) {
		@JsonAnySetter
		public void refuserChampInconnu(String nom, Object valeur) { throw new IllegalArgumentException("Champ non prévu dans Creation."); }
	}

	public record ChoixPilotage(@NotNull @Pattern(regexp = "MANUEL|AUTOMATIQUE") String pilotage) {
		@JsonAnySetter
		public void refuserChampInconnu(String nom, Object valeur) {
			throw new IllegalArgumentException("Champ non prévu dans ChoixPilotage.");
		}
	}

	private final RegistreServicesDeManche services;
	private final SocleDeManche socle;
	private final PilotageDeSoireeDemonstration pilotage;
	private final TransactionTemplate lecture;

	public ManchesDeTestControleur(RegistreServicesDeManche services, SocleDeManche socle,
			PilotageDeSoireeDemonstration pilotage) {
		this.services = services;
		this.socle = socle;
		this.pilotage = pilotage;
		lecture = new TransactionTemplate(socle.transactions());
		lecture.setReadOnly(true);
	}

	@PutMapping("/api/test/soirees/{soireeId}/pilotage")
	public ResponseEntity<Map<String, Object>> choisirPilotage(@PathVariable UUID soireeId,
			@Valid @RequestBody ChoixPilotage choix, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		return socle.verrous().executer(soireeId, () -> {
			lecture.executeWithoutResult(statut -> {
				if (!socle.pilotage().estModifiable(soireeId)) {
					throw new Refuse(409, "ETAT_INCOMPATIBLE",
							"Le Pilotage d'une Soirée enregistrée est celui choisi à sa création.").exception();
				}
				if (socle.depot().deLaSoiree(soireeId).stream().anyMatch(manche -> manche.getEtat() != EtatManche.PRETE)) {
					throw new Refuse(409, "ETAT_INCOMPATIBLE", "La Soirée a commencé : son Pilotage ne change plus.").exception();
				}
			});
			pilotage.choisir(soireeId, "AUTOMATIQUE".equals(choix.pilotage()));
			return ResponseEntity.ok().cacheControl(CacheControl.noStore())
					.body(Map.of("soireeId", soireeId, "pilotage", choix.pilotage()));
		});
	}

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
