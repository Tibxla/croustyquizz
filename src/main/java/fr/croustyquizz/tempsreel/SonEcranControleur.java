package fr.croustyquizz.tempsreel;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Refuse;
import tools.jackson.databind.JsonNode;

@RestController
class SonEcranControleur {
	private final ServiceSonEcran service;

	SonEcranControleur(ServiceSonEcran service) { this.service = service; }

	@PutMapping("/api/soirees/{soireeId}/son-ecran")
	ResponseEntity<SignalSonEcran> signaler(@PathVariable UUID soireeId,
			@Valid @RequestBody SignalSonEcran signal, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ECRAN);
		if (!signal.sonActive().isBoolean()) {
			throw new Refuse(400, "REQUETE_INVALIDE", "L’état du son doit être un booléen.").exception();
		}
		service.signaler(acteur, UUID.fromString(signal.abonnementId()), signal.sonActive().asBoolean());
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(signal);
	}

	record SignalSonEcran(
			@NotNull @Pattern(regexp = "[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}") String abonnementId,
			@NotNull JsonNode sonActive) {
		@JsonAnySetter
		public void refuserChampInconnu(String nom, Object valeur) {
			throw new IllegalArgumentException("Champ non prévu dans le signal du son de l’Écran de salle.");
		}
	}
}
