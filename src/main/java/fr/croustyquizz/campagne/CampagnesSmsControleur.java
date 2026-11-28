package fr.croustyquizz.campagne;

import java.net.URI;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import fr.croustyquizz.animateur.ResolveurAnimateurConnecte;
import fr.croustyquizz.manche.Refuse;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping(CampagnesSmsControleur.ROUTE)
public class CampagnesSmsControleur {
	public static final String ROUTE = "/api/campagnes-sms";

	private final ServiceCampagnesSms service;

	CampagnesSmsControleur(ServiceCampagnesSms service) {
		this.service = service;
	}

	@PostMapping("/apercu")
	ResponseEntity<ServiceCampagnesSms.Apercu> apercu(HttpServletRequest requete, @RequestBody JsonNode corps) {
		UUID lieuId = ResolveurAnimateurConnecte.resoudre(requete).lieuId();
		return sansCache(service.apercu(lieuId, lireTexte(corps)));
	}

	@PostMapping
	ResponseEntity<CampagneSms> creer(HttpServletRequest requete, @RequestHeader("Idempotency-Key") UUID cle,
			@RequestBody JsonNode corps) {
		UUID lieuId = ResolveurAnimateurConnecte.resoudre(requete).lieuId();
		var creation = service.creer(lieuId, cle, lireTexte(corps));
		if (!creation.nouvelle()) {
			return sansCache(creation.campagne());
		}
		URI adresse = UriComponentsBuilder.fromPath(requete.getContextPath() + ROUTE)
				.pathSegment(creation.campagne().id().toString()).build().toUri();
		return ResponseEntity.created(adresse).cacheControl(CacheControl.noStore()).body(creation.campagne());
	}

	@GetMapping
	ResponseEntity<ServiceCampagnesSms.Liste> lister(HttpServletRequest requete) {
		UUID lieuId = ResolveurAnimateurConnecte.resoudre(requete).lieuId();
		return sansCache(service.lister(lieuId));
	}

	@GetMapping("/{campagneId}")
	ResponseEntity<CampagneSms.Detail> consulter(HttpServletRequest requete, @PathVariable UUID campagneId) {
		UUID lieuId = ResolveurAnimateurConnecte.resoudre(requete).lieuId();
		return sansCache(service.consulter(lieuId, campagneId));
	}

	private static String lireTexte(JsonNode corps) {
		if (!corps.isObject() || corps.size() != 1 || !corps.path("texte").isString()) {
			throw new Refuse(400, "REQUETE_INVALIDE", "La demande doit contenir le texte du SMS.").exception();
		}
		return corps.path("texte").asString();
	}

	private static <T> ResponseEntity<T> sansCache(T corps) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corps);
	}
}
