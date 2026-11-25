package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.SessionAnimateurHttp;
import fr.croustyquizz.TestcontainersConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** listerSoirees sur un vrai serveur et un vrai PostgreSQL : chaque Lieu ne voit que ses Soirées. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SoireesDuLieuHttpTest {
	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final String SOIREES = "/api/soirees";

	@LocalServerPort
	private int port;
	@Autowired
	private JsonMapper json;
	@Autowired
	private JdbcTemplate jdbc;

	private SessionAnimateurHttp lieuA;
	private SessionAnimateurHttp lieuB;

	@BeforeEach
	void connecterDeuxLieux() throws Exception {
		lieuA = SessionAnimateurHttp.ouvrir(port, json);
		lieuB = SessionAnimateurHttp.ouvrir(port, json);
	}

	@Test
	void leLieuANeVoitQueSesSoireesAvecLeurEtatDeLaPlusRecenteALaPlusAncienne() throws Exception {
		String terminee = creer(lieuA, "Quiz de la rentrée", "2026-09-03T20:00:00Z");
		String enCours = creer(lieuA, "Karaoké du vendredi", "2026-11-20T20:00:00Z");
		String ouverte = creer(lieuA, "Blind test du jeudi", "2026-11-24T20:00:00Z");
		String preparee = creer(lieuA, "Quiz de Noël", "2026-11-25T09:00:00Z");
		for (String id : new String[] {terminee, enCours, ouverte}) {
			var ouverture = envoyer(HttpMethod.POST, SOIREES + "/" + id + "/ouverture", lieuA.entetes());
			ContratApi.verifier(CONTRAT, "ouvrirSoiree", ouverture);
		}
		jdbc.update("update soiree set etat = 'TERMINEE' where id = ?::uuid", terminee);
		jdbc.update("update soiree set etat = 'EN_COURS' where id = ?::uuid", enCours);
		String soireeDeB = creer(lieuB, "Quiz du Lieu B", "2026-11-25T10:00:00Z");
		String sansLieu = creer(lieuA, "Ancienne Soirée sans Lieu", "2026-11-25T11:00:00Z");
		jdbc.update("update soiree set lieu_id = null where id = ?::uuid", sansLieu);

		JsonNode soireesDeA = lister(lieuA).path("soirees");
		assertThat(soireesDeA.valueStream().map(soiree -> soiree.path("id").asString()).toList())
				.containsExactly(preparee, ouverte, enCours, terminee);
		assertThat(soireesDeA.valueStream().map(soiree -> soiree.path("etat").asString()).toList())
				.containsExactly("PREPARATION", "OUVERTE", "EN_COURS", "TERMINEE");
		assertThat(soireesDeA.get(0).path("titre").asString()).isEqualTo("Quiz de Noël");
		assertThat(soireesDeA.get(1).path("codeAcces").asString()).matches("[A-Z0-9]{6}");

		JsonNode soireesDeB = lister(lieuB).path("soirees");
		assertThat(soireesDeB.valueStream().map(soiree -> soiree.path("id").asString()).toList())
				.containsExactly(soireeDeB);
	}

	@Test
	void unLieuSansSoireeRecoitUneListeVide() throws Exception {
		assertThat(lister(SessionAnimateurHttp.ouvrir(port, json)).path("soirees").isEmpty()).isTrue();
	}

	@Test
	void sansSessionOuAvecUnBearerLaListeEstRefusee() throws Exception {
		verifierRefus(envoyer(HttpMethod.GET, SOIREES, new HttpHeaders()));
		var avecBearer = lieuA.entetes();
		avecBearer.setBearerAuth("demo:ANIMATEUR:00000001-0000-4000-8000-000000000000:00000002-0000-4000-8000-000000000000");
		verifierRefus(envoyer(HttpMethod.GET, SOIREES, avecBearer));
	}

	private JsonNode lister(SessionAnimateurHttp session) throws Exception {
		var liste = envoyer(HttpMethod.GET, SOIREES, session.entetes());
		ContratApi.verifier(CONTRAT, "listerSoirees", liste);
		assertThat(liste.getStatusCode().value()).isEqualTo(200);
		assertThat(liste.getHeaders().getCacheControl()).isEqualTo("no-store");
		return json.readTree(liste.getBody());
	}

	private String creer(SessionAnimateurHttp session, String titre, String creeeLe) throws Exception {
		var creation = RestClient.create("http://localhost:" + port).post().uri(SOIREES)
				.headers(entetes -> entetes.addAll(session.entetes())).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("titre", titre, "format", "SOLO", "pilotage", "MANUEL")).retrieve().toEntity(String.class);
		ContratApi.verifier(CONTRAT, "creerSoiree", creation);
		String id = json.readTree(creation.getBody()).path("id").asString();
		// L'ordre de création est fixé ici, pour ne pas dépendre de l'horloge du serveur.
		jdbc.update("update soiree set creee_le = ? where id = ?::uuid", Timestamp.from(Instant.parse(creeeLe)), id);
		return id;
	}

	private void verifierRefus(ResponseEntity<String> reponse) throws Exception {
		ContratApi.verifier(CONTRAT, "listerSoirees", reponse);
		assertThat(reponse.getStatusCode().value()).isEqualTo(401);
		assertThat(json.readTree(reponse.getBody()).path("code").asString()).isEqualTo("AUTHENTIFICATION_REQUISE");
		assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("no-store");
	}

	private ResponseEntity<String> envoyer(HttpMethod methode, String route, HttpHeaders entetes) {
		var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route)
				.headers(envoi -> envoi.addAll(entetes));
		if (route.endsWith("/ouverture")) {
			requete.header("Idempotency-Key", UUID.randomUUID().toString());
		}
		return requete.retrieve().onStatus(statut -> statut.isError(), (demande, reponse) -> { })
				.toEntity(String.class);
	}
}
