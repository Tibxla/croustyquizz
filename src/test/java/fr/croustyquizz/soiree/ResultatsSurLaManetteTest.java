package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
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

/**
 * Une Manette rechargée retrouve sa Réponse et son résultat par HTTP.
 * Une fausse implémentation tient lieu du Quiz (#14).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, ResultatsSurLaManetteTest.Resultats.class })
class ResultatsSurLaManetteTest {

	private static final String CONTRAT = "docs/api/openapi.yaml";

	@LocalServerPort
	private int port;

	@Autowired
	private JsonMapper json;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private FauxResultats resultats;

	@AfterEach
	void effacer() {
		resultats.vider();
		jdbc.update("delete from acces_ecran");
		jdbc.update("delete from joueur");
		jdbc.update("delete from soiree");
	}

	@Test
	void uneManetteRechargeeRetrouveSaReponseEtSonResultatSansCeuxDesAutres() throws Exception {
		JsonNode soiree = soireeOuverte();
		UUID soireeId = UUID.fromString(soiree.path("id").asString());
		JsonNode lea = rejoindre(soiree.path("codeAcces").asString(), "Léa");
		JsonNode tom = rejoindre(soiree.path("codeAcces").asString(), "Tom");
		UUID leaId = UUID.fromString(lea.at("/joueur/id").asString());

		var accuse = new ResultatsDeSoiree.AccuseReponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), Instant.parse("2026-11-24T20:15:03.250Z"));
		var resultat = new ResultatsDeSoiree.ResultatPersonnel(accuse.etapeId(), true, 87);
		resultats.donner(leaId, accuse, resultat);

		String route = "/api/soirees/" + soireeId + "/etat/manette";
		var vueDeLea = envoyer(HttpMethod.GET, route, null, lea.path("jetonReconnexion").asString());
		ContratApi.verifier(CONTRAT, "retrouverEtatManette", vueDeLea);
		JsonNode vue = json.readTree(vueDeLea.getBody()).path("vue");
		assertThat(vue.at("/derniereReponse/envoiId").asString()).isEqualTo(accuse.envoiId().toString());
		assertThat(vue.at("/derniereReponse/propositionId").asString()).isEqualTo(accuse.propositionId().toString());
		assertThat(Instant.parse(vue.at("/derniereReponse/recueLe").asString())).isEqualTo(accuse.recueLe());
		assertThat(vue.at("/resultatPersonnel/pointsGagnes").asInt()).isEqualTo(87);
		assertThat(vue.at("/resultatPersonnel/juste").asBoolean()).isTrue();

		var vueDeTom = envoyer(HttpMethod.GET, route, null, tom.path("jetonReconnexion").asString());
		ContratApi.verifier(CONTRAT, "retrouverEtatManette", vueDeTom);
		JsonNode autre = json.readTree(vueDeTom.getBody()).path("vue");
		assertThat(autre.path("derniereReponse").isNull()).isTrue();
		assertThat(autre.path("resultatPersonnel").isNull()).isTrue();
	}

	/** Ce que la vraie implémentation du Quiz rendra, fixé par le test. */
	static final class FauxResultats implements ResultatsDeSoiree {

		private final Map<UUID, AccuseReponse> accuses = new ConcurrentHashMap<>();
		private final Map<UUID, ResultatPersonnel> resultats = new ConcurrentHashMap<>();

		void donner(UUID joueurId, AccuseReponse accuse, ResultatPersonnel resultat) {
			accuses.put(joueurId, accuse);
			resultats.put(joueurId, resultat);
		}

		void vider() {
			accuses.clear();
			resultats.clear();
		}

		@Override
		public Optional<AccuseReponse> derniereReponse(UUID soireeId, UUID joueurId) {
			return Optional.ofNullable(accuses.get(joueurId));
		}

		@Override
		public Optional<ResultatPersonnel> resultatPersonnel(UUID soireeId, UUID joueurId) {
			return Optional.ofNullable(resultats.get(joueurId));
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Resultats {
		@Bean
		FauxResultats fauxResultats() {
			return new FauxResultats();
		}
	}

	private JsonNode soireeOuverte() {
		String id = json.readTree(envoyer(HttpMethod.POST, "/api/soirees",
				Map.of("titre", "Quiz du jeudi", "format", "SOLO", "pilotage", "MANUEL"), null).getBody())
				.path("id").asString();
		return json.readTree(envoyer(HttpMethod.POST, "/api/soirees/" + id + "/ouverture", null, null).getBody());
	}

	private JsonNode rejoindre(String code, String pseudo) {
		return json.readTree(envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", code, "pseudo", pseudo), null).getBody());
	}

	private ResponseEntity<String> envoyer(HttpMethod methode, String route, Object corps, String jeton) {
		var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route);
		if (jeton != null) {
			requete.header("Authorization", "Bearer " + jeton);
		}
		if (route.endsWith("/ouverture")) {
			requete.header("Idempotency-Key", UUID.randomUUID().toString());
		}
		if (corps != null) {
			requete.contentType(MediaType.APPLICATION_JSON).body(corps);
		}
		return requete.retrieve().onStatus(statut -> statut.isError(), (demande, reponse) -> { })
				.toEntity(String.class);
	}

}
