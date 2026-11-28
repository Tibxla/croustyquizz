package fr.croustyquizz.contact;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import fr.croustyquizz.animateur.ResolveurAnimateurConnecte;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Refuse;
import fr.croustyquizz.tempsreel.AccesAuxVues;
import tools.jackson.databind.JsonNode;

@RestController
public class ContactsControleur {
	public static final String ROUTE = "/api/contacts";
	public static final String EXPORT = ROUTE + "/export";
	public static final String INSCRIPTION = "/api/soirees/{soireeId}/contact";
	private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	record Liste(List<ServiceContacts.ContactActif> contacts) { }

	private final ServiceContacts service;

	ContactsControleur(ServiceContacts service) {
		this.service = service;
	}

	@GetMapping(INSCRIPTION)
	ResponseEntity<ServiceContacts.Information> informer(@PathVariable UUID soireeId, Acteur acteur) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
		return sansCache(service.informer(soireeId));
	}

	@PostMapping(path = INSCRIPTION, consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<ServiceContacts.Recu> inscrire(@PathVariable UUID soireeId, Acteur acteur, @RequestBody JsonNode corps) {
		AccesAuxVues.verifier(acteur, soireeId, Acteur.Type.JOUEUR);
		if (!corps.path("consentement").asBoolean(false) || !corps.path("consentement").isBoolean()) {
			throw new Refuse(422, "CONSENTEMENT_REQUIS", "Coche la case si tu souhaites recevoir les offres par SMS.")
					.exception();
		}
		var coordonnees = CoordonneesContact.de(texte(corps, "prenom"), texte(corps, "nom"), texte(corps, "numero"));
		return sansCache(service.inscrire(soireeId, coordonnees, texte(corps, "mention"), texte(corps, "declaration")));
	}

	@PostMapping(path = ROUTE + "/desinscription", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<Void> desinscrire(@RequestBody JsonNode corps) {
		service.desinscrire(texte(corps, "cle"));
		return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
	}

	@GetMapping(ROUTE)
	ResponseEntity<Liste> lister(HttpServletRequest requete) {
		UUID lieu = ResolveurAnimateurConnecte.resoudre(requete).lieuId();
		return sansCache(new Liste(service.lister(lieu)));
	}

	@GetMapping(EXPORT)
	ResponseEntity<String> exporter(HttpServletRequest requete) {
		UUID lieu = ResolveurAnimateurConnecte.resoudre(requete).lieuId();
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(CSV)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("contacts.csv").build().toString())
				.body(ExportContactsCsv.composer(service.lister(lieu)));
	}

	private static String texte(JsonNode corps, String champ) {
		var valeur = corps.path(champ);
		if (!valeur.isString()) {
			throw new Refuse(400, "REQUETE_INVALIDE", "Les champs du formulaire sont incomplets.").exception();
		}
		return valeur.asString();
	}

	private static <T> ResponseEntity<T> sansCache(T corps) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corps);
	}
}
