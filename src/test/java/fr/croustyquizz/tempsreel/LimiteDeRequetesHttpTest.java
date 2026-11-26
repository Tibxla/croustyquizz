package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.HorlogeFactice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limite de requêtes de bout en bout, sur PostgreSQL : seuils par défaut d'application.yaml, horloge factice. Les
 * adresses passent par X-Forwarded-For, que Tomcat ne croit que depuis un relais local ou privé, comme Funnel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, LimiteDeRequetesHttpTest.TempsDuTest.class})
class LimiteDeRequetesHttpTest {

	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final String SOIREES = "/api/soirees";
	private static final String ANIMATEURS = "/api/animateurs";
	private static final String SESSION = ANIMATEURS + "/session";
	private static final String MOT_DE_PASSE = "les-étoiles-de-test";

	@LocalServerPort private int port;
	@Autowired private JsonMapper json;
	@Autowired private HorlogeFactice horloge;
	private final HttpClient clientHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private final RestClient http = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(clientHttp)).build();

	@TestConfiguration(proxyBeanMethods = false)
	static class TempsDuTest {
		@Bean @Primary HorlogeFactice horlogeDesLimites() { return new HorlogeFactice(); }
	}

	@AfterEach
	void fermer() {
		clientHttp.close();
	}

	@Test
	void lesConnexionsDepuisUneMemeAdresseSontLimiteesMemeAvecDesEmailsDifferents() throws Exception {
		var navigateur = navigateur("198.51.100.51");
		for (int i = 0; i < 20; i++) {
			verifier("connecterAnimateur", connecter(navigateur, "essai-" + i + "@exemple.test", MOT_DE_PASSE), 401);
		}
		String email = "autre-essai@exemple.test";
		verifierLimite("connecterAnimateur", connecter(navigateur, email, MOT_DE_PASSE), "3");
		verifier("connecterAnimateur", connecter(navigateur("198.51.100.52"), email, MOT_DE_PASSE), 401);

		horloge.avancer(Duration.ofSeconds(3).minusMillis(1));
		verifierLimite("connecterAnimateur", connecter(navigateur, email, MOT_DE_PASSE), "1");
		horloge.avancer(Duration.ofMillis(1));
		verifier("connecterAnimateur", connecter(navigateur, email, MOT_DE_PASSE), 401);
	}

	@Test
	void laLimiteSuitLEmailIndependammentDeSaCasseEtDeLAdresse() throws Exception {
		var navigateur = navigateur("198.51.100.55");
		String email = "élise.inconnue@exemple.test";
		for (int i = 0; i < 4; i++) {
			verifier("connecterAnimateur", connecter(navigateur, email, MOT_DE_PASSE), 401);
		}

		// Le succès d'un autre Animateur, sur le même réseau, laisse le dernier essai de cet e-mail disponible.
		var voisin = navigateur("198.51.100.55");
		String autreEmail = "autre-animateur@exemple.test";
		verifier("inscrireAnimateur", inscrire(voisin, autreEmail), 201);
		verifier("connecterAnimateur", connecter(voisin, autreEmail, MOT_DE_PASSE), 200);
		verifier("connecterAnimateur", connecter(navigateur, email, MOT_DE_PASSE), 401);

		var autreReseau = navigateur("198.51.100.56");
		String emailMajuscule = "ÉLISE.INCONNUE@EXEMPLE.TEST";
		verifierLimite("connecterAnimateur", connecter(autreReseau, emailMajuscule, MOT_DE_PASSE), "30");
		horloge.avancer(Duration.ofSeconds(30).minusMillis(1));
		verifierLimite("connecterAnimateur", connecter(autreReseau, emailMajuscule, MOT_DE_PASSE), "1");
		horloge.avancer(Duration.ofMillis(1));
		verifier("connecterAnimateur", connecter(autreReseau, emailMajuscule, MOT_DE_PASSE), 401);
	}

	@Test
	void laLimiteDInscriptionParAdresseSeRouvreAuDelaiAnnonce() throws Exception {
		var navigateur = navigateur("198.51.100.53");
		String premierEmail = "premiere-inscription@exemple.test";
		verifier("inscrireAnimateur", inscrire(navigateur, premierEmail), 201);
		for (int i = 0; i < 9; i++) {
			verifier("inscrireAnimateur", inscrire(navigateur, premierEmail), 409);
		}

		String autreEmail = "autre-inscription@exemple.test";
		verifierLimite("inscrireAnimateur", inscrire(navigateur, autreEmail), "60");
		verifier("inscrireAnimateur", inscrire(navigateur("198.51.100.54"), autreEmail), 201);

		horloge.avancer(Duration.ofSeconds(60).minusMillis(1));
		String dernierEmail = "derniere-inscription@exemple.test";
		verifierLimite("inscrireAnimateur", inscrire(navigateur, dernierEmail), "1");
		horloge.avancer(Duration.ofMillis(1));
		verifier("inscrireAnimateur", inscrire(navigateur, dernierEmail), 201);
	}

	@Test
	void uneRafaleDeBuzzEstRefuseeSansGenerLesAutresJoueursPuisSeRouvre() throws Exception {
		SoireeOuverte soiree = ouvrirSoiree("198.51.100.1");
		String lea = rejoindre(soiree, "Léa", "198.51.100.1");
		String sam = rejoindre(soiree, "Sam", "198.51.100.1");
		for (int i = 0; i < 10; i++) {
			verifier("envoyerBuzz", buzzer(soiree, lea), 200);
		}

		ResponseEntity<String> refus = buzzer(soiree, lea);
		JsonNode probleme = verifier("envoyerBuzz", refus, 429);
		assertThat(probleme.path("code").asString()).isEqualTo("TROP_DE_REQUETES");
		assertThat(refus.getHeaders().getFirst("Retry-After")).isEqualTo("1");
		assertThat(refus.getHeaders().getCacheControl()).isEqualTo("no-store");

		assertThat(verifier("envoyerBuzz", buzzer(soiree, sam), 200).path("rang").asLong()).isEqualTo(11);
		horloge.avancer(Duration.ofMillis(200));
		// Le Buzz refusé n'a laissé aucune trace : Léa reprend au rang suivant.
		assertThat(verifier("envoyerBuzz", buzzer(soiree, lea), 200).path("rang").asLong()).isEqualTo(12);
	}

	@Test
	void desCodesEssayesEnRafaleSontRefusesSansJamaisRefuserLeBonCodePuisLaLimiteSeRouvre() throws Exception {
		SoireeOuverte soiree = ouvrirSoiree("198.51.100.2");
		for (int i = 0; i < 20; i++) {
			JsonNode inconnu = verifier("rejoindreSoiree", arriver(codeInconnu(soiree), "Intrus", "203.0.113.10"), 404);
			assertThat(inconnu.path("code").asString()).isEqualTo("CODE_ACCES_INVALIDE");
		}

		ResponseEntity<String> refus = arriver(codeInconnu(soiree), "Intrus", "203.0.113.10");
		JsonNode probleme = verifier("rejoindreSoiree", refus, 429);
		assertThat(probleme.path("code").asString()).isEqualTo("TROP_DE_REQUETES");
		assertThat(probleme.path("detail").asString()).isEqualTo("Trop de requêtes en peu de temps. Réessaie dans 3 secondes.");
		assertThat(refus.getHeaders().getFirst("Retry-After")).isEqualTo("3");
		assertThat(refus.getHeaders().getCacheControl()).isEqualTo("no-store");
		// Même réseau, bon code : la salle arrive malgré les essais épuisés de son Wi-Fi.
		verifier("rejoindreSoiree", arriver(soiree.code(), "Voisine", "203.0.113.10"), 201);
		verifier("rejoindreSoiree", arriver(codeInconnu(soiree), "Intrus", "203.0.113.11"), 404);

		horloge.avancer(Duration.ofSeconds(3));
		verifier("rejoindreSoiree", arriver(codeInconnu(soiree), "Patiente", "203.0.113.10"), 404);
	}

	@Test
	void uneSalleDerriereUnMemeWifiArriveDUnCoupSansConsommerSesEssaisDeCode() throws Exception {
		SoireeOuverte soiree = ouvrirSoiree("198.51.100.3");
		for (int i = 1; i <= 30; i++) {
			verifier("rejoindreSoiree", arriver(soiree.code(), "Joueur " + i, "203.0.113.20"), 201);
		}
		verifier("rejoindreSoiree", arriver(soiree.code(), "Joueur 1", "203.0.113.20"), 409);

		verifier("rejoindreSoiree", arriver(codeInconnu(soiree), "Distrait", "203.0.113.20"), 404);
	}

	@Test
	void lAccesDEcranEtLeQrCodeOntLeurPropreLimite() throws Exception {
		SoireeOuverte soiree = ouvrirSoiree("198.51.100.4");
		for (int i = 0; i < 9; i++) {
			verifier("creerAccesEcran", envoyer(HttpMethod.POST, soiree.route() + "/acces-ecran", soiree.animateur(), null,
					"198.51.100.4"), 200);
		}
		ResponseEntity<String> refusEcran = envoyer(HttpMethod.POST, soiree.route() + "/acces-ecran", soiree.animateur(),
				null, "198.51.100.4");
		assertThat(verifier("creerAccesEcran", refusEcran, 429).path("code").asString()).isEqualTo("TROP_DE_REQUETES");
		assertThat(refusEcran.getHeaders().getFirst("Retry-After")).isEqualTo("6");

		for (int i = 0; i < 10; i++) {
			ResponseEntity<String> qrCode = envoyer(HttpMethod.GET, soiree.route() + "/qrcode", soiree.cleEcran(), null,
					"198.51.100.4");
			assertThat(qrCode.getStatusCode().value()).isEqualTo(200);
			ContratApi.verifier(CONTRAT, "consulterQrCodeSoiree", qrCode);
		}
		ResponseEntity<String> refusQrCode = envoyer(HttpMethod.GET, soiree.route() + "/qrcode", soiree.cleEcran(), null,
				"198.51.100.4");
		assertThat(verifier("consulterQrCodeSoiree", refusQrCode, 429).path("code").asString()).isEqualTo("TROP_DE_REQUETES");
		assertThat(refusQrCode.getHeaders().getFirst("Retry-After")).isEqualTo("1");
	}

	@Test
	void soixanteArriveesSimultaneesDepuisLeMemeWifiPassentToutes() throws Exception {
		SoireeOuverte soiree = ouvrirSoiree("198.51.100.5");
		List<Future<ResponseEntity<String>>> arrivees = new ArrayList<>();
		try (var executeur = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 1; i <= 60; i++) {
				String pseudo = "Manette " + i;
				arrivees.add(executeur.submit(() -> arriver(soiree.code(), pseudo, "203.0.113.30")));
			}
			for (var arrivee : arrivees) {
				verifier("rejoindreSoiree", arrivee.get(15, TimeUnit.SECONDS), 201);
			}
		}
		verifier("rejoindreSoiree", arriver(codeInconnu(soiree), "Distraite", "203.0.113.30"), 404);
	}

	private SoireeOuverte ouvrirSoiree(String adresse) throws Exception {
		JsonNode creation = verifier("creerSoiree", envoyer(HttpMethod.POST, SOIREES, null,
				Map.of("titre", "Soirée limitée", "format", "SOLO", "pilotage", "MANUEL"), adresse), 201);
		UUID id = UUID.fromString(creation.path("id").asString());
		String animateur = "demo:ANIMATEUR:" + id + ":" + UUID.randomUUID();
		String route = SOIREES + "/" + id;
		JsonNode ouverte = verifier("ouvrirSoiree", envoyer(HttpMethod.POST, route + "/ouverture", animateur, null, adresse), 200);
		JsonNode ecran = verifier("creerAccesEcran", envoyer(HttpMethod.POST, route + "/acces-ecran", animateur, null, adresse),
				200);
		return new SoireeOuverte(route, ouverte.path("codeAcces").asString(), animateur, ecran.path("cleEcran").asString());
	}

	private String rejoindre(SoireeOuverte soiree, String pseudo, String adresse) throws Exception {
		return verifier("rejoindreSoiree", arriver(soiree.code(), pseudo, adresse), 201).path("jetonReconnexion").asString();
	}

	private ResponseEntity<String> arriver(String code, String pseudo, String adresse) {
		return envoyer(HttpMethod.POST, SOIREES + "/rejoindre", null, Map.of("codeAcces", code, "pseudo", pseudo), adresse);
	}

	private ResponseEntity<String> buzzer(SoireeOuverte soiree, String jeton) {
		return envoyer(HttpMethod.POST, soiree.route() + "/buzz", jeton, Map.of("envoiId", UUID.randomUUID()), "198.51.100.99");
	}

	private static String codeInconnu(SoireeOuverte soiree) {
		return "ZZZZZZ".equals(soiree.code()) ? "YYYYYY" : "ZZZZZZ";
	}

	private Navigateur navigateur(String adresse) throws Exception {
		var reponse = http.get().uri("http://localhost:" + port + "/api/csrf")
				.header("X-Forwarded-For", adresse).retrieve().toEntity(String.class);
		JsonNode jeton = verifier("obtenirCsrf", reponse, 200);
		String cookie = reponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
		return new Navigateur(adresse, cookie, jeton.path("token").asString());
	}

	private ResponseEntity<String> connecter(Navigateur navigateur, String email, String motDePasse) {
		return envoyerCompte(navigateur, SESSION, Map.of("email", email, "motDePasse", motDePasse));
	}

	private ResponseEntity<String> inscrire(Navigateur navigateur, String email) {
		return envoyerCompte(navigateur, ANIMATEURS, Map.of("email", email, "motDePasse", MOT_DE_PASSE,
				"nom", "Camille", "lieu", Map.of("nom", "Les Étoiles", "ville", "Nanterre")));
	}

	private ResponseEntity<String> envoyerCompte(Navigateur navigateur, String route, Map<String, ?> corps) {
		return http.post().uri("http://localhost:" + port + route)
				.header("X-Forwarded-For", navigateur.adresse()).header(HttpHeaders.COOKIE, navigateur.cookie())
				.header("X-CSRF-TOKEN", navigateur.csrf()).contentType(MediaType.APPLICATION_JSON).body(corps)
				.retrieve().onStatus(statut -> statut.isError(), (q, r) -> { }).toEntity(String.class);
	}

	private void verifierLimite(String operation, ResponseEntity<String> reponse, String delai) throws Exception {
		assertThat(verifier(operation, reponse, 429).path("code").asString()).isEqualTo("TROP_DE_REQUETES");
		assertThat(reponse.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo(delai);
		assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("no-store");
	}

	private ResponseEntity<String> envoyer(HttpMethod methode, String route, String secret, Object corps, String adresse) {
		var requete = http.method(methode).uri("http://localhost:" + port + route)
				.header("X-Forwarded-For", adresse)
				.header("X-CSRF-TOKEN", "demonstration");
		if (secret != null) { requete.headers(entetes -> entetes.setBearerAuth(secret)); }
		if (corps == null) { requete.header("Idempotency-Key", UUID.randomUUID().toString()); }
		else { requete.contentType(MediaType.APPLICATION_JSON).body(corps); }
		return requete.retrieve().onStatus(statut -> statut.isError(), (q, r) -> { }).toEntity(String.class);
	}

	private JsonNode verifier(String operation, ResponseEntity<String> reponse, int statut) throws Exception {
		assertThat(reponse.getStatusCode().value()).as("%s : %s", operation, reponse.getBody()).isEqualTo(statut);
		ContratApi.verifier(CONTRAT, operation, reponse);
		return json.readTree(reponse.getBody());
	}

	private record SoireeOuverte(String route, String code, String animateur, String cleEcran) { }

	private record Navigateur(String adresse, String cookie, String csrf) { }

}
