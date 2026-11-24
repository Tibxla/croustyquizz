package fr.croustyquizz.soiree;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.EnvoiDeCommande;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.ReponsesDeCommande;
import fr.croustyquizz.tempsreel.AccesAuxVues;

/** Entrée commune des Buzz d'une Manette dans une Soirée enregistrée. */
@RestController
class BuzzDeSoireeControleur {

	private static final Pattern UUID_COMPLET = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
	private final ServiceBuzz service;

	BuzzDeSoireeControleur(ServiceBuzz service) {
		this.service = service;
	}

	@PostMapping("/api/soirees/{soireeId}/buzz")
	ResponseEntity<?> buzzer(@PathVariable UUID soireeId, Acteur acteur,
			@RequestHeader(name = "Idempotency-Key", required = false) String entete,
			@Valid @RequestBody(required = false) Saisie saisie) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
		UUID cle = entete == null ? null : lireUuid(entete);
		UUID envoiId = saisie == null ? cle : lireUuid(saisie.envoiId());
		if (envoiId == null || cle != null && !cle.equals(envoiId)) {
			throw new Refuse(400, "REQUETE_INVALIDE", "Fournis un identifiant d’envoi. Le corps et l’en-tête doivent concorder.")
					.exception();
		}
		String route = "/api/soirees/" + soireeId + "/buzz";
		var envoi = new EnvoiDeCommande(acteur, route, envoiId, Map.of());
		return ReponsesDeCommande.http(service.buzzer(envoi), route);
	}

	private static UUID lireUuid(String valeur) {
		if (!UUID_COMPLET.matcher(valeur).matches()) {
			throw new Refuse(400, "REQUETE_INVALIDE", "L’identifiant d’envoi doit être un UUID.").exception();
		}
		return UUID.fromString(valeur);
	}

	record Saisie(@NotNull String envoiId) {
		@JsonAnySetter
		public void refuserChampInconnu(String nom, Object valeur) {
			throw new IllegalArgumentException("Champ non prévu dans un envoi de Buzz.");
		}
	}
}
