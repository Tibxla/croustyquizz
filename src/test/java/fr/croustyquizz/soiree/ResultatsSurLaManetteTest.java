package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
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
 * Une Manette rechargée retrouve sa Réponse déjà enregistrée, son résultat et le
 * Classement général par HTTP, tels que les fournit le Mode de jeu. La vraie
 * source est le Quiz (#14) : une fausse implémentation en tient lieu ici.
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

	@Autowired
	private ServiceSoiree service;

	@AfterEach
	void effacer() {
		resultats.vider();
		jdbc.update("delete from acces_ecran");
		jdbc.update("delete from joueur");
		jdbc.update("delete from soiree");
	}

	@Test
	void unEchecDAssemblageAnnuleLArriveeEtLibereLePseudo() {
		JsonNode soiree = soireeOuverte();
		UUID soireeId = UUID.fromString(soiree.path("id").asString());
		String code = soiree.path("codeAcces").asString();
		resultats.soireeIndisponible = soireeId;

		assertThatThrownBy(() -> service.rejoindre(code, "Léa"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Classement indisponible");
		assertThat(service.joueurs(soireeId)).isEmpty();

		resultats.soireeIndisponible = null;
		ArriveeDeJoueur arrivee = service.rejoindre(code, "Léa");
		assertThat(arrivee.jeton()).isNotNull();
		assertThat(service.joueurs(soireeId)).extracting(Joueur::getId).containsExactly(arrivee.joueur().getId());
	}

	@Test
	void uneManetteRechargeeRetrouveSaReponseSonResultatEtLeClassementSansCeuxDesAutres() throws Exception {
		JsonNode soiree = soireeOuverte();
		UUID soireeId = UUID.fromString(soiree.path("id").asString());
		JsonNode lea = rejoindre(soiree.path("codeAcces").asString(), "Léa");
		JsonNode tom = rejoindre(soiree.path("codeAcces").asString(), "Tom");
		UUID leaId = UUID.fromString(lea.at("/joueur/id").asString());

		var accuse = new ResultatsDeSoiree.AccuseReponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), Instant.parse("2026-11-24T20:15:03.250Z"));
		var resultat = new ResultatsDeSoiree.ResultatPersonnel(accuse.etapeId(), true, 87);
		var ligne = new ResultatsDeSoiree.LigneClassement(leaId, "Léa", 1, 87, 1, 2_750);
		resultats.donner(soireeId, leaId, accuse, resultat, List.of(ligne));
		// L'arrivée suivante publie de nouvelles vues avec les résultats fournis ci-dessus.
		rejoindre(soiree.path("codeAcces").asString(), "Camille");

		String route = "/api/soirees/" + soireeId + "/etat/manette";
		var vueDeLea = envoyer(HttpMethod.GET, route, null, lea.path("jetonReconnexion").asString());
		ContratApi.verifier(CONTRAT, "retrouverEtatManette", vueDeLea);
		JsonNode vue = json.readTree(vueDeLea.getBody()).path("vue");
		assertThat(vue.at("/derniereReponse/envoiId").asString()).isEqualTo(accuse.envoiId().toString());
		assertThat(vue.at("/derniereReponse/propositionId").asString()).isEqualTo(accuse.propositionId().toString());
		assertThat(Instant.parse(vue.at("/derniereReponse/recueLe").asString())).isEqualTo(accuse.recueLe());
		assertThat(vue.at("/resultatPersonnel/pointsGagnes").asInt()).isEqualTo(87);
		assertThat(vue.at("/resultatPersonnel/juste").asBoolean()).isTrue();
		assertThat(vue.at("/classement/0/nom").asString()).isEqualTo("Léa");
		assertThat(vue.at("/classement/0/tempsCumuleMs").asLong()).isEqualTo(2_750);

		var vueDeTom = envoyer(HttpMethod.GET, route, null, tom.path("jetonReconnexion").asString());
		ContratApi.verifier(CONTRAT, "retrouverEtatManette", vueDeTom);
		JsonNode autre = json.readTree(vueDeTom.getBody()).path("vue");
		assertThat(autre.path("derniereReponse").isNull()).isTrue();
		assertThat(autre.path("resultatPersonnel").isNull()).isTrue();
		// Le Classement général est commun à toutes les destinations.
		assertThat(autre.at("/classement/0/concurrentId").asString()).isEqualTo(leaId.toString());
	}

	/** Ce que la vraie implémentation du Quiz rendra, fixé par le test. */
	static final class FauxResultats implements ResultatsDeSoiree {

		private final Map<UUID, List<LigneClassement>> classements = new ConcurrentHashMap<>();
		private final Map<UUID, AccuseReponse> accuses = new ConcurrentHashMap<>();
		private final Map<UUID, ResultatPersonnel> resultats = new ConcurrentHashMap<>();
		private volatile UUID soireeIndisponible;

		void donner(UUID soireeId, UUID joueurId, AccuseReponse accuse, ResultatPersonnel resultat,
				List<LigneClassement> classement) {
			classements.put(soireeId, classement);
			accuses.put(joueurId, accuse);
			resultats.put(joueurId, resultat);
		}

		void vider() {
			classements.clear();
			accuses.clear();
			resultats.clear();
			soireeIndisponible = null;
		}

		@Override
		public List<LigneClassement> classement(UUID soireeId) {
			if (soireeId.equals(soireeIndisponible)) {
				throw new IllegalStateException("Classement indisponible");
			}
			return classements.getOrDefault(soireeId, List.of());
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
