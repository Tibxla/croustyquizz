package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.TestcontainersConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Les routes de Soirée sur un vrai serveur et un vrai PostgreSQL, chaque réponse validée par le contrat. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SoireeHttpTest {

	private static final String CONTRAT = "docs/api/openapi.yaml";

	@LocalServerPort
	private int port;

	@Autowired
	private JsonMapper json;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void effacerLaBase() {
		jdbc.update("delete from joueur");
		jdbc.update("delete from soiree");
	}

	@Test
	void creerConsulterOuvrirPuisRejoindreSuiventLeContrat() throws Exception {
		var creation = envoyer(HttpMethod.POST, "/api/soirees",
				Map.of("titre", "Quiz du jeudi", "format", "EQUIPE", "pilotage", "AUTOMATIQUE"));
		ContratApi.verifier(CONTRAT, "creerSoiree", creation);
		assertThat(creation.getStatusCode().value()).isEqualTo(201);
		assertThat(creation.getHeaders().getCacheControl()).isEqualTo("no-store");
		JsonNode soiree = json.readTree(creation.getBody());
		String id = soiree.path("id").asString();
		assertThat(creation.getHeaders().getLocation()).hasToString("/api/soirees/" + id);
		assertThat(soiree.path("etat").asString()).isEqualTo("PREPARATION");
		assertThat(soiree.path("format").asString()).isEqualTo("EQUIPE");
		assertThat(soiree.path("pilotage").asString()).isEqualTo("AUTOMATIQUE");
		assertThat(soiree.has("codeAcces")).isTrue();
		assertThat(soiree.path("codeAcces").isNull()).isTrue();

		var consultation = envoyer(HttpMethod.GET, "/api/soirees/" + id, null);
		ContratApi.verifier(CONTRAT, "consulterSoiree", consultation);
		assertThat(consultation.getBody()).isEqualTo(creation.getBody());

		var ouverture = envoyer(HttpMethod.POST, "/api/soirees/" + id + "/ouverture", null);
		ContratApi.verifier(CONTRAT, "ouvrirSoiree", ouverture);
		String code = json.readTree(ouverture.getBody()).path("codeAcces").asString();
		assertThat(json.readTree(ouverture.getBody()).path("etat").asString()).isEqualTo("OUVERTE");
		var repetition = envoyer(HttpMethod.POST, "/api/soirees/" + id + "/ouverture", null);
		assertThat(repetition.getBody()).isEqualTo(ouverture.getBody());

		var arrivee = envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", code.toLowerCase(), "pseudo", " Léa "));
		ContratApi.verifier(CONTRAT, "rejoindreSoiree", arrivee);
		assertThat(arrivee.getStatusCode().value()).isEqualTo(201);
		assertThat(arrivee.getHeaders().getCacheControl()).isEqualTo("no-store");
		JsonNode acces = json.readTree(arrivee.getBody());
		assertThat(acces.path("soireeId").asString()).isEqualTo(id);
		assertThat(acces.at("/joueur/pseudo").asString()).isEqualTo("Léa");
		assertThat(arrivee.getHeaders().getLocation()).hasToString("/api/soirees/" + id + "/etat/manette");
		String jeton = acces.path("jetonReconnexion").asString();
		byte[] empreinte = jdbc.queryForObject("select empreinte_jeton from joueur where id = ?::uuid", byte[].class,
				acces.at("/joueur/id").asString());
		assertThat(empreinte).isEqualTo(new JetonDeReconnexion(jeton).empreinte());
	}

	@Test
	void lesRefusDeLaConsoleSuiventLeContrat() throws Exception {
		var sansTitre = envoyer(HttpMethod.POST, "/api/soirees",
				Map.of("titre", "   ", "format", "SOLO", "pilotage", "MANUEL"));
		verifierRefus("creerSoiree", sansTitre, 422, "DONNEES_INVALIDES");

		Map<String, Object> sansFormat = new HashMap<>(Map.of("titre", "Quiz", "pilotage", "MANUEL"));
		verifierRefus("creerSoiree", envoyer(HttpMethod.POST, "/api/soirees", sansFormat), 400, "REQUETE_INVALIDE");
		verifierRefus("creerSoiree", envoyer(HttpMethod.POST, "/api/soirees",
				Map.of("titre", "Quiz", "format", "DUO", "pilotage", "MANUEL")), 400, "REQUETE_INVALIDE");

		String inconnue = "/api/soirees/" + UUID.randomUUID();
		verifierRefus("consulterSoiree", envoyer(HttpMethod.GET, inconnue, null), 404, "RESSOURCE_INTROUVABLE");
		verifierRefus("ouvrirSoiree", envoyer(HttpMethod.POST, inconnue + "/ouverture", null), 404,
				"RESSOURCE_INTROUVABLE");
		verifierRefus("consulterSoiree", envoyer(HttpMethod.GET, "/api/soirees/pas-un-uuid", null), 400,
				"REQUETE_INVALIDE");

		String lancee = soireeOuverte().path("id").asString();
		jdbc.update("update soiree set etat = 'EN_COURS' where id = ?::uuid", lancee);
		verifierRefus("ouvrirSoiree", envoyer(HttpMethod.POST, "/api/soirees/" + lancee + "/ouverture", null), 409,
				"ETAT_INCOMPATIBLE");
	}

	@Test
	void lesRefusDeLArriveeSuiventLeContrat() throws Exception {
		JsonNode ouverte = soireeOuverte();
		String code = ouverte.path("codeAcces").asString();

		verifierRefus("rejoindreSoiree", envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", "ZZZZZZ".equals(code) ? "YYYYYY" : "ZZZZZZ", "pseudo", "Léa")), 404,
				"CODE_ACCES_INVALIDE");
		verifierRefus("rejoindreSoiree", envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", code, "pseudo", "  ")), 422, "DONNEES_INVALIDES");
		verifierRefus("rejoindreSoiree", envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", code, "pseudo", "x".repeat(Joueur.LONGUEUR_MAX_PSEUDO + 1))), 422,
				"DONNEES_INVALIDES");

		jdbc.update("update soiree set etat = 'TERMINEE' where id = ?::uuid", ouverte.path("id").asString());
		verifierRefus("rejoindreSoiree", envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", code, "pseudo", "Léa")), 404, "CODE_ACCES_INVALIDE");
		assertThat(jdbc.queryForObject("select count(*) from joueur", Integer.class)).isZero();
	}

	private JsonNode soireeOuverte() {
		String id = json.readTree(envoyer(HttpMethod.POST, "/api/soirees",
				Map.of("titre", "Quiz du jeudi", "format", "SOLO", "pilotage", "MANUEL")).getBody()).path("id").asString();
		return json.readTree(envoyer(HttpMethod.POST, "/api/soirees/" + id + "/ouverture", null).getBody());
	}

	private void verifierRefus(String operationId, ResponseEntity<String> reponse, int statut, String code)
			throws Exception {
		ContratApi.verifier(CONTRAT, operationId, reponse);
		assertThat(reponse.getStatusCode().value()).isEqualTo(statut);
		assertThat(json.readTree(reponse.getBody()).path("code").asString()).isEqualTo(code);
		assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("no-store");
	}

	private ResponseEntity<String> envoyer(HttpMethod methode, String route, Object corps) {
		var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route);
		if (methode == HttpMethod.POST && route.endsWith("/ouverture")) {
			requete.header("Idempotency-Key", UUID.randomUUID().toString());
		}
		if (corps != null) {
			requete.contentType(MediaType.APPLICATION_JSON).body(corps);
		}
		return requete.retrieve().onStatus(statut -> statut.isError(), (demande, reponse) -> { })
				.toEntity(String.class);
	}

}
