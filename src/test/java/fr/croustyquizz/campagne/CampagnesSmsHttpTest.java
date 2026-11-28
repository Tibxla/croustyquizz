package fr.croustyquizz.campagne;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
import org.springframework.web.client.RestClient;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.SessionAnimateurHttp;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.HorlogeFactice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "croustyquizz.campagnes.tarif-par-segment=7")
@Import({TestcontainersConfiguration.class, CampagnesSmsHttpTest.PortsDeTest.class})
class CampagnesSmsHttpTest {
	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final String ROUTE = "/api/campagnes-sms";
	private static final String APERCU = "previsualiserCampagneSms";
	private static final String CREER = "creerCampagneSms";
	private static final String LISTER = "listerCampagnesSms";
	private static final String CONSULTER = "consulterCampagneSms";
	private static final String MESSAGE = "Quiz jeudi !";
	private static final Instant SAMEDI_19H59 = Instant.parse("2026-11-28T18:59:00Z");
	private static final Instant LUNDI_8H = Instant.parse("2026-11-30T07:00:00Z");
	private static final String LIEN = "https://croustyquizz.invalid/stop/";
	private static final String NUMERO_1 = "+33639980001";
	private static final String NUMERO_2 = "+33639980002";
	private static final String NUMERO_3 = "+33639980003";

	@LocalServerPort
	private int port;
	@Autowired
	private JsonMapper json;
	@Autowired
	private HorlogeFactice horloge;
	@Autowired
	private ContactsFactices contacts;
	@Autowired
	private SmsFactices sms;
	@Autowired
	private PlanificationCampagnes planification;

	@Test
	void unLieuSansContactsPeutPrevisualiserMaisNePeutPasCreer() throws Exception {
		var lieu = SessionAnimateurHttp.ouvrir(port, json);
		var apercu = appeler(HttpMethod.POST, ROUTE + "/apercu", APERCU, lieu.entetes(), MESSAGE, 200);
		assertThat(apercu.path("destinataires").asInt()).isZero();
		assertThat(apercu.path("coutEstimeCentimes").asInt()).isZero();
		assertThat(apercu.path("exemple").asString()).isEqualTo(MESSAGE + "\nSTOP : https://croustyquizz.invalid/stop/exemple");

		var entetes = lieu.entetes();
		entetes.set("Idempotency-Key", UUID.randomUUID().toString());
		assertThat(appeler(HttpMethod.POST, ROUTE, CREER, entetes, MESSAGE, 409).path("code").asString())
				.isEqualTo("AUCUN_CONTACT_ELIGIBLE");
		var liste = appeler(HttpMethod.GET, ROUTE, LISTER, lieu.entetes(), null, 200);
		assertThat(liste.path("campagnes").size()).isZero();
		assertThat(liste.path("destinatairesEligibles").asInt()).isZero();
	}

	@Test
	void exigeLaSessionEtLeCsrfEtRefuseLesTextesInvalides() throws Exception {
		var lieu = SessionAnimateurHttp.ouvrir(port, json);
		appeler(HttpMethod.GET, ROUTE, LISTER, new HttpHeaders(), null, 401);
		var sansCsrf = lieu.entetes();
		sansCsrf.remove("X-CSRF-TOKEN");
		appeler(HttpMethod.POST, ROUTE + "/apercu", APERCU, sansCsrf, MESSAGE, 403);
		appeler(HttpMethod.POST, ROUTE, CREER, sansCsrf, MESSAGE, 403);
		appeler(HttpMethod.POST, ROUTE + "/apercu", APERCU, lieu.entetes(), " \n ", 422);
		appeler(HttpMethod.POST, ROUTE + "/apercu", APERCU, lieu.entetes(), "🎤".repeat(201), 422);
		appeler(HttpMethod.POST, ROUTE, CREER, lieu.entetes(), MESSAGE, 400);
	}

	@Test
	void envoieDansLeCreneauEtReprendLesCampagnesDuesEnEcartantLeContactDesinscrit() throws Exception {
		var lieu = SessionAnimateurHttp.ouvrir(port, json);
		var autreLieu = SessionAnimateurHttp.ouvrir(port, json);
		UUID lieuId = UUID.fromString(appeler(HttpMethod.GET, "/api/animateurs/session", "consulterAnimateur",
				lieu.entetes(), null, 200).path("lieuId").asString());
		var premier = new ContactsDuLieu.Contact(UUID.randomUUID(), NUMERO_1, LIEN + "premier");
		var deuxieme = new ContactsDuLieu.Contact(UUID.randomUUID(), NUMERO_2, LIEN + "second");
		var troisieme = new ContactsDuLieu.Contact(UUID.randomUUID(), NUMERO_3, LIEN + "troisieme");
		contacts.parLieu.put(lieuId, List.of(premier, deuxieme));

		String texte = "a".repeat(160);
		var apercu = appeler(HttpMethod.POST, ROUTE + "/apercu", APERCU, lieu.entetes(), texte, 200);
		assertThat(apercu.path("immediat").asBoolean()).isTrue();
		assertThat(apercu.path("segments").asInt()).isEqualTo(2);
		assertThat(apercu.path("destinataires").asInt()).isEqualTo(2);
		assertThat(apercu.path("coutEstimeCentimes").asInt()).isEqualTo(28);
		assertThat(apercu.path("exemple").asString()).doesNotContain(premier.lienDeDesinscription());

		var immediate = appeler(HttpMethod.POST, ROUTE, CREER, clePour(lieu), MESSAGE, 201);
		assertThat(immediate.path("etat").asString()).isEqualTo("TERMINEE");
		assertThat(immediate.path("envoyes").asInt()).isEqualTo(1);
		assertThat(immediate.path("echoues").asInt()).isEqualTo(1);
		assertThat(sms.tentatives).containsExactlyInAnyOrder(MESSAGE + "\nSTOP : " + premier.lienDeDesinscription(),
				MESSAGE + "\nSTOP : " + deuxieme.lienDeDesinscription());
		String idImmediat = immediate.path("id").asString();
		var detailImmediat = appeler(HttpMethod.GET, ROUTE + "/" + idImmediat, CONSULTER, lieu.entetes(), null, 200);
		assertThat(detailImmediat.path("envois").valueStream().map(envoi -> envoi.path("statut").asString()).toList())
				.containsExactlyInAnyOrder("ENVOYE", "ECHOUE");
		assertThat(detailImmediat.toString()).doesNotContain(NUMERO_1, NUMERO_2, LIEN);

		horloge.avancer(Duration.ofMinutes(1));
		String annonce = "Karaoké demain !";
		var cle = clePour(lieu);
		var programmee = appeler(HttpMethod.POST, ROUTE, CREER, cle, annonce, 201);
		String id = programmee.path("id").asString();
		var vue = appeler(HttpMethod.POST, ROUTE + "/apercu", APERCU, lieu.entetes(), annonce, 200);
		assertThat(vue.path("envoiPrevuLe").asString()).isEqualTo(LUNDI_8H.toString());
		assertThat(vue.path("immediat").asBoolean()).isFalse();
		assertThat(programmee.path("etat").asString()).isEqualTo("PROGRAMMEE");
		assertThat(appeler(HttpMethod.POST, ROUTE, CREER, cle, " " + annonce, 200).path("id").asString()).isEqualTo(id);
		assertThat(appeler(HttpMethod.POST, ROUTE, CREER, cle, "Autre SMS", 409).path("code").asString())
				.isEqualTo("CLE_IDEMPOTENCE_REUTILISEE");

		appeler(HttpMethod.GET, ROUTE + "/" + id, CONSULTER, autreLieu.entetes(), null, 404);
		assertThat(appeler(HttpMethod.GET, ROUTE, LISTER, autreLieu.entetes(), null, 200).path("campagnes").size()).isZero();
		contacts.parLieu.put(lieuId, List.of(premier, troisieme));
		horloge.avancer(Duration.between(horloge.maintenant(), LUNDI_8H).minusSeconds(1));
		assertThat(sms.tentatives).hasSize(2);
		horloge.avancer(Duration.ofSeconds(1));
		verifierTerminee(lieu, id, premier.id());
		assertThat(sms.tentatives).hasSize(3).contains(annonce + "\nSTOP : " + premier.lienDeDesinscription());

		// L’application s’arrête avant mardi 8 h et redémarre mardi à 20 h.
		horloge.avancer(Duration.ofHours(12));
		var cleReprise = clePour(lieu);
		var aReprendre = appeler(HttpMethod.POST, ROUTE, CREER, cleReprise, "Quiz vendredi !", 201);
		String idReprise = aReprendre.path("id").asString();
		planification.annulerMinuteur();
		contacts.parLieu.put(lieuId, List.of(premier));
		horloge.avancer(Duration.ofHours(24));
		assertThat(sms.tentatives).hasSize(3);
		planification.recharger();
		horloge.avancer(Duration.ZERO);
		var reportee = appeler(HttpMethod.GET, ROUTE + "/" + idReprise, CONSULTER, lieu.entetes(), null, 200);
		assertThat(reportee.path("campagne").path("etat").asString()).isEqualTo("PROGRAMMEE");
		assertThat(reportee.path("campagne").path("envoiPrevuLe").asString()).isEqualTo("2026-12-02T07:00:00Z");
		assertThat(sms.tentatives).hasSize(3);
		horloge.avancer(Duration.ofHours(12));
		verifierTerminee(lieu, idReprise, premier.id());
		assertThat(sms.tentatives).hasSize(4);
		assertThat(appeler(HttpMethod.POST, ROUTE, CREER, cleReprise, "Quiz vendredi !", 200).path("etat").asString())
				.isEqualTo("TERMINEE");
		var campagnes = appeler(HttpMethod.GET, ROUTE, LISTER, lieu.entetes(), null, 200).path("campagnes");
		assertThat(campagnes.valueStream().map(campagne -> campagne.path("id").asString()).toList())
				.containsExactly(idReprise, id, idImmediat);
	}

	private void verifierTerminee(SessionAnimateurHttp lieu, String id, UUID contactId) throws Exception {
		var detail = appeler(HttpMethod.GET, ROUTE + "/" + id, CONSULTER, lieu.entetes(), null, 200);
		assertThat(detail.path("campagne").path("etat").asString()).isEqualTo("TERMINEE");
		assertThat(detail.path("campagne").path("envoyes").asInt()).isEqualTo(1);
		assertThat(detail.path("campagne").path("echoues").asInt()).isZero();
		assertThat(detail.path("envois").valueStream().map(envoi -> envoi.path("contactId").asString()).toList())
				.containsExactly(contactId.toString());
	}

	private static HttpHeaders clePour(SessionAnimateurHttp lieu) {
		var entetes = lieu.entetes();
		entetes.set("Idempotency-Key", UUID.randomUUID().toString());
		return entetes;
	}

	private JsonNode appeler(HttpMethod methode, String route, String operation, HttpHeaders entetes, String texte,
			int statut) throws Exception {
		var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route)
				.headers(envoi -> envoi.addAll(entetes));
		if (texte != null) {
			requete.body(Map.of("texte", texte));
		}
		var reponse = requete.retrieve().onStatus(code -> code.isError(), (demande, refus) -> { }).toEntity(String.class);
		assertThat(reponse.getStatusCode().value()).isEqualTo(statut);
		assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("no-store");
		ContratApi.verifier(CONTRAT, operation, reponse);
		return json.readTree(reponse.getBody());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class PortsDeTest {
		@Bean
		@Primary
		HorlogeFactice horlogeCampagnes() {
			return new HorlogeFactice(SAMEDI_19H59);
		}

		@Bean
		@Primary
		ContactsFactices contactsFactices() {
			return new ContactsFactices();
		}

		@Bean
		SmsFactices smsFactices() {
			return new SmsFactices();
		}
	}

	static final class ContactsFactices implements ContactsDuLieu {
		private final Map<UUID, List<Contact>> parLieu = new ConcurrentHashMap<>();

		@Override
		public List<Contact> eligibles(UUID lieuId) {
			return parLieu.getOrDefault(lieuId, List.of());
		}
	}

	static final class SmsFactices implements EnvoiDeSms {
		private final List<String> tentatives = new CopyOnWriteArrayList<>();

		@Override
		public Resultat envoyer(String texte, String numeroInternational) {
			tentatives.add(texte);
			return NUMERO_2.equals(numeroInternational) ? new Echec("Échec de l’envoi.") : new Envoye("sms-simule");
		}
	}
}
