package fr.croustyquizz.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.SessionAnimateurHttp;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.HorlogeFactice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ContactsHttpTest.TempsDeTest.class})
class ContactsHttpTest {
	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final String CONTACTS = "/api/contacts";
	private static final String INSCRIRE = "inscrireContact";
	private static final String NUMERO = "+33639980032";
	private static final Instant DIMANCHE = Instant.parse("2026-11-29T10:00:00Z");

	@LocalServerPort
	private int port;
	@Autowired
	private JsonMapper json;
	@Autowired
	private HorlogeFactice horloge;

	@Test
	void refuseLeConsentementAbsentOuDecocheEtLeNumeroInvalide() throws Exception {
		var lieu = SessionAnimateurHttp.ouvrir(port, json);
		var joueur = rejoindre(lieu, "Camille");
		var demande = demande(joueur, NUMERO);
		demande.remove("consentement");
		assertThat(inscrire(joueur, demande, 422).path("code").asString()).isEqualTo("CONSENTEMENT_REQUIS");
		demande.put("consentement", false);
		assertThat(inscrire(joueur, demande, 422).path("code").asString()).isEqualTo("CONSENTEMENT_REQUIS");
		assertThat(inscrire(joueur, demande(joueur, "06 39 98"), 422).path("code").asString()).isEqualTo("NUMERO_INVALIDE");
		demande.put("consentement", true);
		demande.put("mention", "Ancienne mention");
		assertThat(inscrire(joueur, demande, 409).path("code").asString()).isEqualTo("MENTION_MODIFIEE");
		assertThat(lister(lieu).size()).isZero();
	}

	@Test
	void normaliseEtMetAJourUnContactParLieuSansRediffuserSonLien() throws Exception {
		var lieu = SessionAnimateurHttp.ouvrir(port, json);
		var joueur = rejoindre(lieu, "Camille");
		Instant debut = horloge.maintenant();
		var creation = inscrire(joueur, demande(joueur, "06 39 98 00 32"), 200);
		assertThat(creation.path("lienDeDesinscription").asString()).startsWith("/desinscription.html#cle=");
		assertThat(creation.path("consentiLe").asString()).isEqualTo(debut.toString());

		horloge.avancer(Duration.ofSeconds(1));
		var autreJoueur = rejoindre(lieu, "Morgan");
		var demande = demande(autreJoueur, "0033 (6) 39-98-00-32");
		demande.put("prenom", "=Camille");
		demande.put("nom", "D’Exemple, \"Jr.\"");
		assertThat(inscrire(autreJoueur, demande, 200).has("lienDeDesinscription")).isFalse();

		var autres = SessionAnimateurHttp.ouvrir(port, json);
		assertThat(lister(autres).size()).isZero();
		assertThat(exporter(autres)).doesNotContain(NUMERO);
		var joueurAutreLieu = rejoindre(autres, "Alex");
		appeler(HttpMethod.GET, joueur.route(), "consulterInformationContact", joueurAutreLieu.entetes(), null, 404);
		appeler(HttpMethod.GET, joueur.route(), "consulterInformationContact", new HttpHeaders(), null, 401);
		appeler(HttpMethod.GET, CONTACTS, "listerContacts", joueur.entetes(), null, 401);
		inscrire(joueurAutreLieu, demande(joueurAutreLieu, NUMERO), 200);
		assertThat(lister(autres).size()).isEqualTo(1);

		var contacts = lister(lieu);
		assertThat(contacts.size()).isEqualTo(1);
		var fiche = contacts.get(0);
		assertThat(fiche.path("prenom").asString()).isEqualTo("=Camille");
		assertThat(fiche.path("numero").asString()).isEqualTo(NUMERO);
		assertThat(fiche.path("mention").asString()).isEqualTo(joueur.mention() + "\n" + joueur.declaration());
		assertThat(fiche.path("consentiLe").asString()).isEqualTo(debut.plusSeconds(1).toString());
		assertThat(exporter(lieu)).contains("\"'=Camille\",\"D’Exemple, \"\"Jr.\"\"\",\"'" + NUMERO + "\"");
	}

	@Test
	void retireLeContactEtExclutSaFicheDeLaCampagneDejaProgrammee() throws Exception {
		Instant maintenant = horloge.maintenant();
		Instant dimanche = maintenant.atOffset(java.time.ZoneOffset.UTC)
				.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SUNDAY)).toInstant();
		horloge.avancer(Duration.between(maintenant, dimanche));
		var lieu = SessionAnimateurHttp.ouvrir(port, json);
		var joueur = rejoindre(lieu, "Camille");
		var creation = inscrire(joueur, demande(joueur, NUMERO), 200);
		var autreLieu = SessionAnimateurHttp.ouvrir(port, json);
		var autreJoueur = rejoindre(autreLieu, "Morgan");
		inscrire(autreJoueur, demande(autreJoueur, NUMERO), 200);
		var sms = Map.of("texte", "Le prochain Quiz du Balcon");
		assertThat(apercu(lieu, sms)).isEqualTo(1);
		var entetes = lieu.entetes();
		entetes.set("Idempotency-Key", UUID.randomUUID().toString());
		var campagne = lire(appeler(HttpMethod.POST, "/api/campagnes-sms", "creerCampagneSms", entetes, sms, 201));
		assertThat(campagne.path("etat").asString()).isEqualTo("PROGRAMMEE");

		var fragment = URI.create(creation.path("lienDeDesinscription").asString()).getFragment();
		desinscrire("inconnue", 404);
		desinscrire(fragment.substring("cle=".length()), 204);
		desinscrire(fragment.substring("cle=".length()), 204);
		assertThat(lister(lieu).size()).isZero();
		assertThat(exporter(lieu)).doesNotContain(NUMERO);
		assertThat(apercu(lieu, sms)).isZero();
		assertThat(apercu(autreLieu, sms)).isEqualTo(1);
		assertThat(inscrire(joueur, demande(joueur, NUMERO), 409).path("code").asString()).isEqualTo("CONTACT_DESINSCRIT");

		Instant prochain = Instant.parse(campagne.path("envoiPrevuLe").asString());
		horloge.avancer(Duration.between(horloge.maintenant(), prochain));
		var detail = lire(appeler(HttpMethod.GET, "/api/campagnes-sms/" + campagne.path("id").asString(),
				"consulterCampagneSms", lieu.entetes(), null, 200));
		assertThat(detail.path("campagne").path("etat").asString()).isEqualTo("TERMINEE");
		assertThat(detail.path("envois").size()).isZero();
	}

	@Test
	void exclutLesCoordonneesExpireesEtCreeUnNouveauLienApresExpiration() throws Exception {
		var lieu = SessionAnimateurHttp.ouvrir(port, json);
		var joueur = rejoindre(lieu, "Camille");
		var creation = inscrire(joueur, demande(joueur, NUMERO), 200);
		Instant expiration = horloge.maintenant().atOffset(java.time.ZoneOffset.UTC).plusYears(3).toInstant();
		horloge.avancer(Duration.between(horloge.maintenant(), expiration));
		assertThat(lister(lieu).size()).isZero();
		var renouvellement = inscrire(joueur, demande(joueur, NUMERO), 200);
		var ancienLien = creation.path("lienDeDesinscription").asString();
		assertThat(renouvellement.path("lienDeDesinscription").asString()).isNotEqualTo(ancienLien);
		desinscrire(URI.create(ancienLien).getFragment().substring("cle=".length()), 404);
		assertThat(lister(lieu).size()).isEqualTo(1);
	}

	private int apercu(SessionAnimateurHttp lieu, Map<String, String> sms) throws Exception {
		return lire(appeler(HttpMethod.POST, "/api/campagnes-sms/apercu", "previsualiserCampagneSms",
				lieu.entetes(), sms, 200)).path("destinataires").asInt();
	}

	private Joueur rejoindre(SessionAnimateurHttp lieu, String pseudo) throws Exception {
		var creation = lire(appeler(HttpMethod.POST, "/api/soirees", "creerSoiree", lieu.entetes(),
				Map.of("titre", "Le Quiz fictif", "format", "SOLO", "pilotage", "MANUEL"), 201));
		String soiree = "/api/soirees/" + creation.path("id").asString();
		var entetes = lieu.entetes();
		entetes.set("Idempotency-Key", UUID.randomUUID().toString());
		var ouverture = lire(appeler(HttpMethod.POST, soiree + "/ouverture", "ouvrirSoiree", entetes, null, 200));
		var acces = lire(appeler(HttpMethod.POST, "/api/soirees/rejoindre", "rejoindreSoiree", new HttpHeaders(),
				Map.of("codeAcces", ouverture.path("codeAcces").asString(), "pseudo", pseudo), 201));
		var bearer = new HttpHeaders();
		bearer.setBearerAuth(acces.path("jetonReconnexion").asString());
		var route = soiree + "/contact";
		var information = lire(appeler(HttpMethod.GET, route, "consulterInformationContact", bearer, null, 200));
		return new Joueur(route, bearer, information.path("mention").asString(), information.path("declaration").asString());
	}

	private static Map<String, Object> demande(Joueur joueur, String numero) {
		return new HashMap<>(Map.of("prenom", "Camille", "nom", "Exemple", "numero", numero,
				"consentement", true, "mention", joueur.mention(), "declaration", joueur.declaration()));
	}

	private JsonNode inscrire(Joueur joueur, Map<String, Object> demande, int statut) throws Exception {
		return lire(appeler(HttpMethod.POST, joueur.route(), INSCRIRE, joueur.entetes(), demande, statut));
	}

	private JsonNode lister(SessionAnimateurHttp lieu) throws Exception {
		return lire(appeler(HttpMethod.GET, CONTACTS, "listerContacts", lieu.entetes(), null, 200)).path("contacts");
	}

	private String exporter(SessionAnimateurHttp lieu) throws Exception {
		var reponse = appeler(HttpMethod.GET, CONTACTS + "/export", "exporterContacts", lieu.entetes(), null, 200);
		assertThat(reponse.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment; filename=\"contacts.csv\"");
		return reponse.getBody();
	}

	private void desinscrire(String cle, int statut) throws Exception {
		appeler(HttpMethod.POST, CONTACTS + "/desinscription", "desinscrireContact", new HttpHeaders(), Map.of("cle", cle), statut);
	}

	private JsonNode lire(ResponseEntity<String> reponse) {
		return json.readTree(reponse.getBody());
	}

	private ResponseEntity<String> appeler(HttpMethod methode, String route, String operation, HttpHeaders entetes,
			Map<String, ?> corps, int statut) throws Exception {
		var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route)
				.headers(envoi -> envoi.addAll(entetes));
		if (corps != null) {
			requete.body(corps);
		}
		var reponse = requete.retrieve().onStatus(code -> code.isError(), (demande, refus) -> { }).toEntity(String.class);
		assertThat(reponse.getStatusCode().value()).as(operation).isEqualTo(statut);
		assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("no-store");
		ContratApi.verifier(CONTRAT, operation, reponse);
		return reponse;
	}

	private record Joueur(String route, HttpHeaders entetes, String mention, String declaration) { }

	@TestConfiguration(proxyBeanMethods = false)
	static class TempsDeTest {
		@Bean
		@Primary
		HorlogeFactice horlogeContacts() {
			return new HorlogeFactice(DIMANCHE);
		}
	}
}
