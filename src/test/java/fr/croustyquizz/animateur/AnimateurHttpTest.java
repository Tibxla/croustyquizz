package fr.croustyquizz.animateur;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.TestcontainersConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AnimateurHttpTest {

	private static final String INSCRIPTION = "/api/animateurs";
	private static final String SESSION = "/api/animateurs/session";
	private static final String CSRF = "X-CSRF-TOKEN";

	@LocalServerPort
	private int port;

	@Autowired
	private JsonMapper json;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void obtientUnJetonCsrfAvantConnexion() throws Exception {
		try (var client = HttpClient.newHttpClient()) {
			var reponse = obtenirCsrf(client);
			assertThat(reponse.headers().firstValue("Set-Cookie").orElseThrow())
					.startsWith("JSESSIONID=").contains("HttpOnly", "SameSite=Lax");
		}
	}

	@Test
	void inscritLePremierAnimateurEtRefuseUnDoublonSansCreerDeLieu(CapturedOutput journal) throws Exception {
		String motDePasse = "bistrot-des-étoiles";
		try (var client = HttpClient.newHttpClient()) {
			var csrf = obtenirCsrf(client);
			var entetes = Map.of("Cookie", cookie(csrf), CSRF, jeton(csrf));
			var creation = envoyer(client, "POST", INSCRIPTION, entetes,
					inscription("Camille@exemple.test", motDePasse, "Les Étoiles"));
			JsonNode compte = verifier("inscrireAnimateur", 201, creation);

			assertThat(compte.at("/animateur/nom").asString()).isEqualTo("Camille");
			assertThat(compte.at("/animateur/lieuId").asString()).isEqualTo(compte.at("/lieu/id").asString());
			assertThat(compte.at("/lieu/nom").asString()).isEqualTo("Les Étoiles");
			assertThat(compte.at("/lieu/ville").asString()).isEqualTo("Nanterre");
			assertThat(creation.headers().firstValue("Location")).contains(SESSION);
			assertThat(creation.body()).doesNotContain(motDePasse, "Camille@exemple.test");

			var doublon = envoyer(client, "POST", INSCRIPTION, entetes,
					inscription("camille@EXEMPLE.test", motDePasse, "Lieu en doublon"));
			assertThat(verifier("inscrireAnimateur", 409, doublon).path("code").asString())
					.isEqualTo("EMAIL_DEJA_UTILISE");
			assertThat(jdbc.sql("select count(*) from lieu where nom = 'Lieu en doublon'")
					.query(Integer.class).single()).isZero();
			String empreinte = jdbc.sql("select mot_de_passe_hache from animateur where id = cast(:id as uuid)")
					.param("id", compte.at("/animateur/id").asString()).query(String.class).single();
			assertThat(empreinte).startsWith("$2").doesNotContain(motDePasse);
			assertThat(journal.getAll()).doesNotContain(motDePasse);
		}
	}

	@Test
	void connecteParEmailSansDistinctionDeCasseEtRenouvelleLaSession(CapturedOutput journal) throws Exception {
		String prefixe = "é".repeat(127);
		String motDePasse = prefixe + "a";
		String email = "daniele@exemple.test";
		try (var client = HttpClient.newHttpClient()) {
			var csrf = obtenirCsrf(client);
			String cookieAnonyme = cookie(csrf);
			var entetes = Map.of("Cookie", cookieAnonyme, CSRF, jeton(csrf), "X-Forwarded-Proto", "https");
			JsonNode compte = verifier("inscrireAnimateur", 201,
					envoyer(client, "POST", INSCRIPTION, entetes, inscription(email, motDePasse, "Le Balcon")));

			verifierRefus("consulterAnimateur", 401, "AUTHENTIFICATION_REQUISE",
					envoyer(client, "GET", SESSION, Map.of("Cookie", cookieAnonyme), null));
			var mauvaisMotDePasse = envoyer(client, "POST", SESSION, entetes, connexion(email, prefixe + "b"));
			var emailInconnu = envoyer(client, "POST", SESSION, entetes, connexion("inconnu@exemple.test", motDePasse));
			verifierRefus("connecterAnimateur", 401, "IDENTIFIANTS_INVALIDES", mauvaisMotDePasse);
			verifierRefus("connecterAnimateur", 401, "IDENTIFIANTS_INVALIDES", emailInconnu);
			assertThat(emailInconnu.body()).isEqualTo(mauvaisMotDePasse.body());

			var ouverte = envoyer(client, "POST", SESSION, entetes, connexion("DANIELE@exemple.test", motDePasse));
			assertThat(verifier("connecterAnimateur", 200, ouverte)).isEqualTo(compte.path("animateur"));
			assertThat(ouverte.headers().firstValue("Set-Cookie").orElseThrow())
					.contains("HttpOnly", "SameSite=Lax", "Secure");
			String cookieConnecte = cookie(ouverte);
			assertThat(cookieConnecte).isNotEqualTo(cookieAnonyme);

			assertThat(verifier("consulterAnimateur", 200,
					envoyer(client, "GET", SESSION, Map.of("Cookie", cookieConnecte), null)))
					.isEqualTo(compte.path("animateur"));
			verifierRefus("consulterAnimateur", 401, "AUTHENTIFICATION_REQUISE",
					envoyer(client, "GET", SESSION, Map.of("Cookie", cookieAnonyme), null));
			verifierRefus("consulterAnimateur", 401, "AUTHENTIFICATION_REQUISE",
					envoyer(client, "GET", SESSION, Map.of("Cookie", cookieConnecte, "Authorization", "Bearer absent"), null));
			assertThat(journal.getAll()).doesNotContain(motDePasse, prefixe + "b");
		}
	}

	@Test
	void deconnecteAvecLeNouveauJetonEtInvalideLaSession() throws Exception {
		String email = "leo@exemple.test";
		String motDePasse = "un mot de passe de test";
		try (var client = HttpClient.newHttpClient()) {
			var initial = obtenirCsrf(client);
			var entetes = Map.of("Cookie", cookie(initial), CSRF, jeton(initial));
			verifier("inscrireAnimateur", 201,
					envoyer(client, "POST", INSCRIPTION, entetes, inscription(email, motDePasse, "Le Signal")));
			var authentifie = envoyer(client, "POST", SESSION, entetes, connexion(email, motDePasse));
			verifier("connecterAnimateur", 200, authentifie);
			String session = cookie(authentifie);

			verifierRefus("deconnecterAnimateur", 403, "CSRF_INVALIDE",
					envoyer(client, "DELETE", SESSION, Map.of("Cookie", session, CSRF, jeton(initial)), null));
			verifierRefus("deconnecterAnimateur", 401, "AUTHENTIFICATION_REQUISE", envoyer(client, "DELETE",
					SESSION, Map.of("Cookie", session, "Authorization", "Bearer absent"), null));
			var renouvellement = envoyer(client, "GET", "/api/csrf", Map.of("Cookie", session), null);
			verifier("obtenirCsrf", 200, renouvellement);
			var fermeture = envoyer(client, "DELETE", SESSION, Map.of("Cookie", session, CSRF, jeton(renouvellement)), null);
			verifier("deconnecterAnimateur", 204, fermeture);
			assertThat(fermeture.headers().allValues("Set-Cookie"))
					.allSatisfy(entete -> assertThat(entete).contains("HttpOnly", "SameSite=Lax"));

			verifierRefus("consulterAnimateur", 401, "AUTHENTIFICATION_REQUISE",
					envoyer(client, "GET", SESSION, Map.of("Cookie", session), null));
			var apresFermeture = envoyer(client, "GET", "/api/csrf", Map.of("Cookie", session), null);
			verifier("obtenirCsrf", 200, apresFermeture);
			assertThat(cookie(apresFermeture)).isNotEqualTo(session);
			verifierRefus("connecterAnimateur", 403, "CSRF_INVALIDE", envoyer(client, "POST", SESSION,
					Map.of("Cookie", cookie(apresFermeture), CSRF, jeton(renouvellement)), connexion(email, motDePasse)));
			verifierRefus("deconnecterAnimateur", 401, "AUTHENTIFICATION_REQUISE", envoyer(client, "DELETE", SESSION,
					Map.of("Cookie", cookie(apresFermeture), CSRF, jeton(apresFermeture)), null));
		}
	}

	@Test
	void enregistreLEmailEnMinusculesSansDependreDeLaBase() throws Exception {
		String motDePasse = "le mot de passe d’Élise";
		try (var client = HttpClient.newHttpClient()) {
			var csrf = obtenirCsrf(client);
			var entetes = Map.of("Cookie", cookie(csrf), CSRF, jeton(csrf));
			JsonNode compte = verifier("inscrireAnimateur", 201, envoyer(client, "POST", INSCRIPTION, entetes,
					inscription("Élise.Ñúñez@Exemple.test", motDePasse, "L’Écluse")));

			assertThat(jdbc.sql("select email from animateur where id = cast(:id as uuid)")
					.param("id", compte.at("/animateur/id").asString()).query(String.class).single())
					.isEqualTo("élise.ñúñez@exemple.test");
			verifierRefus("inscrireAnimateur", 409, "EMAIL_DEJA_UTILISE", envoyer(client, "POST", INSCRIPTION,
					entetes, inscription("élise.ñúñez@exemple.test", motDePasse, "L’Écluse bis")));
			assertThat(verifier("connecterAnimateur", 200, envoyer(client, "POST", SESSION, entetes,
					connexion("ÉLISE.ÑÚÑEZ@EXEMPLE.TEST", motDePasse)))).isEqualTo(compte.path("animateur"));
		}
	}

	@Test
	void refuseUnSurrogateIsoleDansChaqueChampEnregistre() throws Exception {
		try (var client = HttpClient.newHttpClient()) {
			var csrf = obtenirCsrf(client);
			var entetes = Map.of("Cookie", cookie(csrf), CSRF, jeton(csrf), "Content-Type", "application/json");
			// Corps écrits à la main : la sérialisation Java remplacerait le surrogate isolé par « ? ».
			String modele = """
					{"email":"%s","motDePasse":"un mot de passe correct","nom":"%s",\
					"lieu":{"nom":"%s","ville":"%s"}}""";
			String isole = "\\ud800";
			var corps = List.of(
					modele.formatted(isole + "rose@exemple.test", "Rose", "Le Kiosque", "Nanterre"),
					modele.formatted("rose@exemple.test", "Rose" + isole, "Le Kiosque", "Nanterre"),
					modele.formatted("rose@exemple.test", "Rose", "Le Kiosque" + isole, "Nanterre"),
					modele.formatted("rose@exemple.test", "Rose", "Le Kiosque", "Nanterre" + isole));
			for (String brut : corps) {
				verifierRefus("inscrireAnimateur", 422, "DONNEES_INVALIDES",
						envoyerBrut(client, "POST", INSCRIPTION, entetes, brut));
			}
			assertThat(jdbc.sql("select count(*) from lieu where nom like 'Le Kiosque%'")
					.query(Integer.class).single()).isZero();
			verifierRefus("connecterAnimateur", 422, "DONNEES_INVALIDES", envoyerBrut(client, "POST", SESSION,
					entetes, "{\"email\":\"" + isole + "rose@exemple.test\",\"motDePasse\":\"un mot de passe\"}"));
		}
	}

	@Test
	void exigeUnJetonCsrfDansLEntetePourLesMutationsDuNavigateur() throws Exception {
		String email = "sacha@exemple.test";
		String motDePasse = "encore un mot de passe";
		try (var client = HttpClient.newHttpClient()) {
			var csrf = obtenirCsrf(client);
			String session = cookie(csrf);
			var demande = inscription(email, motDePasse, "La Verrière");
			verifierRefus("inscrireAnimateur", 403, "CSRF_INVALIDE",
					envoyer(client, "POST", INSCRIPTION, Map.of("Cookie", session), demande));
			verifierRefus("inscrireAnimateur", 403, "CSRF_INVALIDE",
					envoyer(client, "POST", INSCRIPTION + "?_csrf=" + jeton(csrf), Map.of("Cookie", session), demande));
			verifierRefus("connecterAnimateur", 403, "CSRF_INVALIDE", envoyer(client, "POST", SESSION,
					Map.of("Cookie", session, CSRF, "jeton-invalide"), connexion(email, motDePasse)));

			var bearer = Map.of("Cookie", session, "Authorization", "Bearer absent");
			verifierRefus("inscrireAnimateur", 403, "ACCES_REFUSE", envoyer(client, "POST", INSCRIPTION, bearer, demande));
			verifierRefus("connecterAnimateur", 403, "ACCES_REFUSE",
					envoyer(client, "POST", SESSION, bearer, connexion(email, motDePasse)));
		}
	}

	@Test
	void distingueUneStructureInvalideDeDonneesHorsBornes(CapturedOutput journal) throws Exception {
		String motDePasse = "trop-court";
		try (var client = HttpClient.newHttpClient()) {
			var csrf = obtenirCsrf(client);
			var entetes = Map.of("Cookie", cookie(csrf), CSRF, jeton(csrf));
			var demande = inscription("maud@exemple.test", motDePasse, "Le Patio");
			verifierRefus("inscrireAnimateur", 422, "DONNEES_INVALIDES",
					envoyer(client, "POST", INSCRIPTION, entetes, demande));

			var avecId = new HashMap<>(demande);
			avecId.put("lieu", Map.of("nom", "Le Patio", "ville", "Nanterre", "id", "lieu-existant"));
			verifierRefus("inscrireAnimateur", 400, "REQUETE_INVALIDE",
					envoyer(client, "POST", INSCRIPTION, entetes, avecId));

			var avecNombre = new HashMap<>(demande);
			avecNombre.put("motDePasse", 123456789012L);
			verifierRefus("inscrireAnimateur", 400, "REQUETE_INVALIDE",
					envoyer(client, "POST", INSCRIPTION, entetes, avecNombre));
			verifierRefus("connecterAnimateur", 422, "DONNEES_INVALIDES",
					envoyer(client, "POST", SESSION, entetes, connexion("adresse-invalide", motDePasse)));
			assertThat(journal.getAll()).doesNotContain(motDePasse);
		}
	}

	private HttpResponse<String> obtenirCsrf(HttpClient client) throws Exception {
		var reponse = envoyer(client, "GET", "/api/csrf", Map.of(), null);
		verifier("obtenirCsrf", 200, reponse);
		return reponse;
	}

	private String jeton(HttpResponse<String> reponse) {
		return json.readTree(reponse.body()).path("token").asString();
	}

	private static String cookie(HttpResponse<String> reponse) {
		return reponse.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
	}

	private static Map<String, Object> inscription(String email, String motDePasse, String nomLieu) {
		return Map.of("email", email, "motDePasse", motDePasse, "nom", "Camille",
				"lieu", Map.of("nom", nomLieu, "ville", "Nanterre"));
	}

	private static Map<String, String> connexion(String email, String motDePasse) {
		return Map.of("email", email, "motDePasse", motDePasse);
	}

	private HttpResponse<String> envoyer(HttpClient client, String methode, String chemin, Map<String, String> entetes,
			Object corps) throws Exception {
		var requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin));
		entetes.forEach(requete::header);
		if (corps == null) {
			requete.method(methode, HttpRequest.BodyPublishers.noBody());
		} else {
			requete.header("Content-Type", "application/json").method(methode,
					HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corps)));
		}
		return client.send(requete.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> envoyerBrut(HttpClient client, String methode, String chemin,
			Map<String, String> entetes, String corps) throws Exception {
		var requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin));
		entetes.forEach(requete::header);
		requete.method(methode, HttpRequest.BodyPublishers.ofString(corps));
		return client.send(requete.build(), HttpResponse.BodyHandlers.ofString());
	}

	private JsonNode verifier(String operation, int statut, HttpResponse<String> reponse) throws Exception {
		assertThat(reponse.statusCode()).as(operation).isEqualTo(statut);
		ContratApi.verifier("docs/api/openapi.yaml", operation, reponse.statusCode(),
				reponse.headers().firstValue("Content-Type").orElse(null), reponse.body());
		assertThat(reponse.headers().firstValue("Cache-Control")).contains("no-store");
		return reponse.body().isEmpty() ? json.getNodeFactory().missingNode() : json.readTree(reponse.body());
	}

	private void verifierRefus(String operation, int statut, String code, HttpResponse<String> reponse) throws Exception {
		assertThat(verifier(operation, statut, reponse).path("code").asString()).isEqualTo(code);
	}
}
