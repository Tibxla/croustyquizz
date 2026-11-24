package fr.croustyquizz.photos;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.manche.ReponsesDeCommande;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/soirees/{soireeId}/photos")
class PhotosControleur {

	private static final String CLE = "Idempotency-Key";
	private final ServicePhotos service;

	PhotosControleur(ServicePhotos service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<?> envoyer(@PathVariable UUID soireeId, @RequestHeader(CLE) UUID cle,
			Acteur acteur, HttpServletRequest requete) throws IOException {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
		if (!estJpeg(requete.getContentType())) {
			throw JpegDeSalle.invalide("Seules les photos JPEG sont acceptées.").exception();
		}
		var jpeg = JpegDeSalle.lire(requete.getInputStream());
		return ReponsesDeCommande.http(service.envoyer(acteur, cle, jpeg), requete.getRequestURI());
	}

	@GetMapping("/{photoId}")
	ResponseEntity<byte[]> consulter(@PathVariable UUID soireeId, @PathVariable UUID photoId, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, null);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_JPEG)
				.header("X-Content-Type-Options", "nosniff").body(service.lire(acteur, photoId));
	}

	@PostMapping("/{photoId}/moderation")
	ResponseEntity<?> moderer(@PathVariable UUID soireeId, @PathVariable UUID photoId, @RequestHeader(CLE) UUID cle,
			@RequestBody JsonNode commande, Acteur acteur, HttpServletRequest requete) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		return ReponsesDeCommande.http(service.moderer(acteur, cle, photoId, decision(commande, "acceptee")),
				requete.getRequestURI());
	}

	@PutMapping("/reglages")
	ResponseEntity<?> regler(@PathVariable UUID soireeId, @RequestHeader(CLE) UUID cle,
			@RequestBody JsonNode commande, Acteur acteur, HttpServletRequest requete) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.ANIMATEUR);
		return ReponsesDeCommande.http(service.regler(acteur, cle, decision(commande, "ouvertes")), requete.getRequestURI());
	}

	private static boolean estJpeg(String type) {
		try {
			return type != null && MediaType.IMAGE_JPEG.equalsTypeAndSubtype(MediaType.parseMediaType(type));
		} catch (InvalidMediaTypeException erreur) {
			return false;
		}
	}

	private static boolean decision(JsonNode commande, String champ) {
		JsonNode valeur = commande.path(champ);
		if (commande.size() != 1 || !valeur.isBoolean()) {
			throw new Refuse(400, "REQUETE_INVALIDE", "La décision est obligatoire et doit être un booléen.").exception();
		}
		return valeur.asBoolean();
	}
}
