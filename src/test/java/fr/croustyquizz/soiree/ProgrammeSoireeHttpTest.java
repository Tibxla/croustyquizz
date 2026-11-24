package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.TestcontainersConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ProgrammeSoireeHttpTest {
	private static final Map<String, Object> QUIZ = Map.of("mode", "QUIZ",
			"reglages", Map.of("nombreEtapes", 3, "tempsParEtapeSecondes", 20));
	private static final Map<String, Object> KARAOKE = Map.of("mode", "KARAOKE", "reglages", Map.of());

	@LocalServerPort
	private int port;
	@Autowired
	private JsonMapper json;
	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void ajouteEnFinEtPublieLeProgrammeDeLaConsole() throws Exception {
		String soiree = creerSoiree();
		JsonNode quiz = ajouter(soiree, QUIZ);
		JsonNode karaoke = ajouter(soiree, KARAOKE);

		assertThat(quiz.path("ordre").intValue()).isZero();
		assertThat(quiz.path("reglages").path("tempsParEtapeSecondes").intValue()).isEqualTo(20);
		assertThat(karaoke.path("ordre").intValue()).isEqualTo(1);
		assertThat(karaoke.path("reglages").path("limitePassagesEnAttente").intValue()).isBetween(1, 20);
		JsonNode capture = console(soiree);
		assertThat(capture.path("revision").longValue()).isEqualTo(2);
		assertThat(capture.at("/vue/programme/0")).isEqualTo(quiz);
		assertThat(capture.at("/vue/programme/1")).isEqualTo(karaoke);
	}

	@Test
	void reordonneEtRetireLesManchesApresOuverture() throws Exception {
		String soiree = creerSoiree();
		String quiz = ajouter(soiree, QUIZ).path("id").asString();
		ouvrir(soiree);
		JsonNode ouverte = console(soiree);
		assertThat(ouverte.at("/vue/soiree/etat").asString()).isEqualTo("OUVERTE");
		assertThat(ouverte.at("/vue/programme/0/id").asString()).isEqualTo(quiz);
		ouvrir(soiree);
		assertThat(console(soiree).path("revision")).isEqualTo(ouverte.path("revision"));
		String karaoke = ajouter(soiree, KARAOKE).path("id").asString();
		JsonNode ordonne = ordonner(soiree, List.of(karaoke, quiz), 200);
		assertThat(ordonne.at("/vue/soiree/etat").asString()).isEqualTo("OUVERTE");
		assertThat(ordonne.at("/vue/programme/0/id").asString()).isEqualTo(karaoke);
		assertThat(ordonne.at("/vue/programme/1/id").asString()).isEqualTo(quiz);
		assertThat(console(soiree).path("revision")).isEqualTo(ordonne.path("revision"));

		retirer(soiree, karaoke, 204);
		JsonNode programme = console(soiree).at("/vue/programme");
		assertThat(programme.size()).isEqualTo(1);
		assertThat(programme.at("/0/id").asString()).isEqualTo(quiz);
		assertThat(programme.at("/0/ordre").intValue()).isZero();
		assertThat(ajouter(soiree, KARAOKE).path("ordre").intValue()).isEqualTo(1);
	}

	@Test
	void refuseLesOrdresInvalidesEtLesManchesEtrangeresSansPublication() throws Exception {
		String soiree = creerSoiree();
		String quiz = ajouter(soiree, QUIZ).path("id").asString();
		String karaoke = ajouter(soiree, KARAOKE).path("id").asString();
		String autre = ajouter(creerSoiree(), QUIZ).path("id").asString();
		assertThat(ordonner(soiree, List.of(quiz, quiz), 400).path("code").asString()).isEqualTo("REQUETE_INVALIDE");
		assertThat(ordonner(soiree, List.of(quiz), 422).path("code").asString()).isEqualTo("DONNEES_INVALIDES");
		assertThat(ordonner(soiree, List.of(autre, karaoke), 422).path("code").asString()).isEqualTo("DONNEES_INVALIDES");
		assertThat(retirer(soiree, autre, 404).path("code").asString()).isEqualTo("RESSOURCE_INTROUVABLE");
		JsonNode capture = console(soiree);
		assertThat(capture.path("revision").longValue()).isEqualTo(2);
		assertThat(capture.at("/vue/programme/0/id").asString()).isEqualTo(quiz);
		assertThat(capture.at("/vue/programme/1/id").asString()).isEqualTo(karaoke);
	}

	@Test
	void refuseLesTroisMutationsDesQueLaSoireeEstEnCours() throws Exception {
		String soiree = creerSoiree();
		String manche = ajouter(soiree, QUIZ).path("id").asString();
		ouvrir(soiree);
		// Le lancement d'une Soirée est livré par #12.
		jdbc.update("update soiree set etat = 'EN_COURS', debutee_le = ouverte_le where id = ?::uuid", soiree);
		var ajout = envoyer("ajouterManche", HttpMethod.POST, route(soiree, "/manches"), animateur(soiree), QUIZ, 409);
		assertThat(ajout.path("code").asString()).isEqualTo("ETAT_INCOMPATIBLE");
		assertThat(ordonner(soiree, List.of(manche), 409).path("code").asString()).isEqualTo("ETAT_INCOMPATIBLE");
		assertThat(retirer(soiree, manche, 409).path("code").asString()).isEqualTo("ETAT_INCOMPATIBLE");
		assertThat(console(soiree).path("revision").longValue()).isEqualTo(2);
	}

	@Test
	void demarreUnKaraokeDuProgrammeParLaRouteDeTest() throws Exception {
		String soiree = creerSoiree();
		String manche = ajouter(soiree, Map.of("mode", "KARAOKE", "reglages", Map.of("limitePassagesEnAttente", 3)))
				.path("id").asString();
		envoyer("docs/api/prototype/openapi.yaml", "demarrerMancheDeTest", HttpMethod.POST,
				"/api/test/soirees/" + soiree + "/manches/" + manche + "/demarrer", animateur(soiree), null, 200);
		JsonNode capture = console(soiree);
		assertThat(capture.at("/vue/mancheActive/id").asString()).isEqualTo(manche);
		assertThat(capture.at("/vue/karaoke/limitePassagesEnAttente").intValue()).isEqualTo(3);
		assertThat(retirer(soiree, manche, 409).path("code").asString()).isEqualTo("ETAT_INCOMPATIBLE");
	}

	@Test
	void refuseLesReglagesHorsContratEtLeMauvaisRole() throws Exception {
		String soiree = creerSoiree();
		String route = route(soiree, "/manches");
		var quizInvalide = Map.of("mode", "QUIZ", "reglages", Map.of("nombreEtapes", 101, "tempsParEtapeSecondes", 20));
		var karaokeInvalide = Map.of("mode", "KARAOKE", "reglages", Map.of("limitePassagesEnAttente", 21));
		assertThat(envoyer("ajouterManche", HttpMethod.POST, route, animateur(soiree), quizInvalide, 400)
				.path("code").asString()).isEqualTo("REQUETE_INVALIDE");
		assertThat(envoyer("ajouterManche", HttpMethod.POST, route, animateur(soiree), karaokeInvalide, 400)
				.path("code").asString()).isEqualTo("REQUETE_INVALIDE");
		assertThat(envoyer("ajouterManche", HttpMethod.POST, route, "demo:ECRAN:" + soiree + ":" + UUID.randomUUID(), QUIZ, 403)
				.path("code").asString()).isEqualTo("ACCES_REFUSE");
		assertThat(console(soiree).at("/vue/programme").size()).isZero();
	}

	private String creerSoiree() throws Exception {
		return envoyer("creerSoiree", HttpMethod.POST, "/api/soirees", null,
				Map.of("titre", "Soirée du jeudi", "format", "SOLO", "pilotage", "MANUEL"), 201).path("id").asString();
	}

	private JsonNode ajouter(String soiree, Map<String, Object> corps) throws Exception {
		return envoyer("ajouterManche", HttpMethod.POST, route(soiree, "/manches"), animateur(soiree), corps, 201);
	}

	private JsonNode console(String soiree) throws Exception {
		return envoyer("retrouverEtatConsole", HttpMethod.GET, route(soiree, "/etat/console"), animateur(soiree), null, 200);
	}

	private void ouvrir(String soiree) throws Exception {
		envoyer("ouvrirSoiree", HttpMethod.POST, route(soiree, "/ouverture"), animateur(soiree), null, 200);
	}

	private JsonNode ordonner(String soiree, List<String> manches, int statut) throws Exception {
		return envoyer("ordonnerManches", HttpMethod.PUT, route(soiree, "/ordre-des-manches"), animateur(soiree),
				Map.of("mancheIds", manches), statut);
	}

	private JsonNode retirer(String soiree, String manche, int statut) throws Exception {
		return envoyer("retirerManche", HttpMethod.DELETE, route(soiree, "/manches/" + manche), animateur(soiree), null, statut);
	}

	private JsonNode envoyer(String operation, HttpMethod methode, String route, String jeton, Object corps, int statut)
			throws Exception {
		return envoyer("docs/api/openapi.yaml", operation, methode, route, jeton, corps, statut);
	}

	private JsonNode envoyer(String contrat, String operation, HttpMethod methode, String route, String jeton, Object corps,
			int statut) throws Exception {
		var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route);
		if (jeton != null) {
			requete.header("Authorization", "Bearer " + jeton);
		}
		requete.header("Idempotency-Key", UUID.randomUUID().toString()).header("X-CSRF-TOKEN", "csrf_demo_000000000");
		if (corps != null) {
			requete.contentType(MediaType.APPLICATION_JSON).body(corps);
		}
		var reponse = requete.retrieve().onStatus(code -> code.isError(), (demande, resultat) -> { }).toEntity(String.class);
		ContratApi.verifier(contrat, operation, reponse);
		assertThat(reponse.getStatusCode().value()).as(operation).isEqualTo(statut);
		assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("no-store");
		return reponse.getBody() == null ? null : json.readTree(reponse.getBody());
	}

	private static String animateur(String soiree) {
		return "demo:ANIMATEUR:" + soiree + ":" + UUID.randomUUID();
	}

	private static String route(String soiree, String suffixe) {
		return "/api/soirees/" + soiree + suffixe;
	}
}
