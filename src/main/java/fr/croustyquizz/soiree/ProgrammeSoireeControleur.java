package fr.croustyquizz.soiree;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import fr.croustyquizz.tempsreel.VueSynchronisee;
import tools.jackson.databind.JsonNode;

/** Commandes du programme, réservées à l'Animateur de la Soirée. */
@RestController
@RequestMapping("/api/soirees/{soireeId}")
class ProgrammeSoireeControleur {
	private static final Pattern FORMAT_UUID = Pattern.compile(
			"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);
	private final ServiceProgramme service;

	ProgrammeSoireeControleur(ServiceProgramme service) {
		this.service = service;
	}

	@PostMapping("/manches")
	ResponseEntity<ProjectionManche> ajouter(@PathVariable UUID soireeId, @RequestBody JsonNode demande, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		JsonNode reglages = demande.path("reglages");
		if (!demande.isObject() || demande.size() != 2 || !reglages.isObject()) {
			throw reglagesInvalides();
		}
		ModeDeJeu mode = switch (demande.path("mode").asString("")) {
			case "QUIZ" -> ModeDeJeu.QUIZ;
			case "KARAOKE" -> ModeDeJeu.KARAOKE;
			case "FREESTYLE" -> ModeDeJeu.FREESTYLE;
			default -> throw reglagesInvalides();
		};
		if (mode == ModeDeJeu.QUIZ && (reglages.size() != 2 || !entierPositif(reglages.path("nombreEtapes"), 100)
				|| !entierPositif(reglages.path("tempsParEtapeSecondes"), 3600))) {
			throw reglagesInvalides();
		}
		return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.ajouter(soireeId, mode, reglages));
	}

	@DeleteMapping("/manches/{mancheId}")
	ResponseEntity<Void> retirer(@PathVariable UUID soireeId, @PathVariable UUID mancheId, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		service.retirer(soireeId, mancheId);
		return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
	}

	@PutMapping("/ordre-des-manches")
	ResponseEntity<VueSynchronisee> ordonner(@PathVariable UUID soireeId, @RequestBody JsonNode demande, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		JsonNode ids = demande.path("mancheIds");
		if (!demande.isObject() || demande.size() != 1 || !ids.isArray() || ids.isEmpty()) {
			throw ordreInvalide();
		}
		var ordre = new LinkedHashSet<UUID>();
		for (JsonNode valeur : ids) {
			String texte = valeur.asString("");
			if (!FORMAT_UUID.matcher(texte).matches() || !ordre.add(UUID.fromString(texte))) {
				throw ordreInvalide();
			}
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.ordonner(soireeId, List.copyOf(ordre)));
	}

	private static boolean entierPositif(JsonNode valeur, int maximum) {
		return valeur.isIntegralNumber() && valeur.canConvertToInt() && valeur.intValue() >= 1 && valeur.intValue() <= maximum;
	}

	private static RuntimeException reglagesInvalides() {
		return new Refuse(400, "REQUETE_INVALIDE", "Le Mode de jeu ou les réglages de la Manche sont invalides.").exception();
	}

	private static RuntimeException ordreInvalide() {
		return new Refuse(400, "REQUETE_INVALIDE", "Indique une liste d’identifiants de Manches distincts.").exception();
	}
}
