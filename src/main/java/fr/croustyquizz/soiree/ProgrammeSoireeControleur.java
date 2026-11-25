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
	/** Bornes du contrat : ReglagesBlindTest.nombreMorceaux et SourceDeMorceaux.idDeezer. */
	private static final int NOMBRE_MAX_MORCEAUX = 50;
	private static final long ID_DEEZER_MAX = 9_007_199_254_740_991L;
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
			case "BLIND_TEST" -> ModeDeJeu.BLIND_TEST;
			case "FREESTYLE" -> ModeDeJeu.FREESTYLE;
			default -> throw reglagesInvalides();
		};
		if (mode == ModeDeJeu.QUIZ && (reglages.size() != 2 || !entierPositif(reglages.path("nombreEtapes"), 100)
				|| !entierPositif(reglages.path("tempsParEtapeSecondes"), 3600))) {
			throw reglagesInvalides();
		}
		if (mode == ModeDeJeu.BLIND_TEST && !reglagesBlindTestValides(reglages)) {
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

	/** ReglagesBlindTest : une source Deezer et de 1 à 50 Morceaux, rien d'autre (la sélection n'y figure jamais). */
	private static boolean reglagesBlindTestValides(JsonNode reglages) {
		JsonNode source = reglages.path("source");
		JsonNode idDeezer = source.path("idDeezer");
		return reglages.size() == 2 && entierPositif(reglages.path("nombreMorceaux"), NOMBRE_MAX_MORCEAUX)
				&& source.isObject() && source.size() == 2
				&& ("PLAYLIST".equals(source.path("type").asString("")) || "GENRE".equals(source.path("type").asString("")))
				&& idDeezer.isIntegralNumber() && idDeezer.canConvertToLong() && idDeezer.longValue() >= 0
				&& idDeezer.longValue() <= ID_DEEZER_MAX;
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
