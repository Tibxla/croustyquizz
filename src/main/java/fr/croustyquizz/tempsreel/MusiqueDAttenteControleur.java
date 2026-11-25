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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Refuse;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/soirees/{soireeId}/musique-attente")
class MusiqueDAttenteControleur {
	private final ServiceMusiqueDAttente service;

	MusiqueDAttenteControleur(ServiceMusiqueDAttente service) {
		this.service = service;
	}

	@PutMapping
	ResponseEntity<Reglage> regler(@PathVariable UUID soireeId, @Valid @RequestBody Reglage reglage, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		service.regler(soireeId, MusiqueDAttente.Etat.valueOf(reglage.etat()));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reglage);
	}

	@PutMapping("/disponibilite")
	ResponseEntity<Disponibilite> signaler(@PathVariable UUID soireeId,
			@Valid @RequestBody Disponibilite disponibilite, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ECRAN);
		if (!disponibilite.morceauxDisponibles().isBoolean()) {
			throw new Refuse(400, "REQUETE_INVALIDE", "La disponibilité des morceaux doit être un booléen.").exception();
		}
		service.signaler(soireeId, disponibilite.morceauxDisponibles().asBoolean());
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(disponibilite);
	}

	record Reglage(@NotNull @Pattern(regexp = "JOUE|COUPEE") String etat) {
		@JsonAnySetter
		public void refuserChampInconnu(String nom, Object valeur) {
			throw new IllegalArgumentException("Champ non prévu dans le réglage de la Musique d’attente.");
		}
	}

	record Disponibilite(@NotNull JsonNode morceauxDisponibles) {
		@JsonAnySetter
		public void refuserChampInconnu(String nom, Object valeur) {
			throw new IllegalArgumentException("Champ non prévu dans le signal de Musique d’attente.");
		}
	}
}
