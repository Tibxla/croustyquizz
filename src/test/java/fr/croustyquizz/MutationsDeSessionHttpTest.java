package fr.croustyquizz;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Chaque opération mutante que le contrat ouvre à la session Animateur exige le jeton CSRF : la liste
 * des routes protégées par Spring Security ne doit oublier aucune route d'Animateur, même ajoutée plus tard.
 * Le refus précède la lecture du corps et de la Soirée : des identifiants quelconques suffisent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MutationsDeSessionHttpTest {

	private static final String CONTRAT = "docs/api/openapi.yaml";

	@LocalServerPort
	private int port;
	@Autowired
	private JsonMapper json;

	@Test
	void chaqueMutationDeLaSessionAnimateurExigeLeCsrf() throws Exception {
		var session = SessionAnimateurHttp.ouvrir(port, json);
		var operations = mutationsDeSession();
		assertThat(operations).extracting(Operation::id)
				.contains("piloterSoiree", "reglerMusiqueDAttente", "reglerPhotos", "modererPhoto");
		for (var operation : operations) {
			var entetes = new HttpHeaders();
			entetes.set(HttpHeaders.COOKIE, session.cookie());
			entetes.set("Idempotency-Key", UUID.randomUUID().toString());
			var reponse = RestClient.create("http://localhost:" + port).method(operation.methode())
					.uri(operation.chemin().replaceAll("\\{[^}]+}", UUID.randomUUID().toString()))
					.headers(envoi -> envoi.addAll(entetes))
					.contentType(MediaType.APPLICATION_JSON).body("{}")
					.retrieve().onStatus(statut -> statut.isError(), (demande, resultat) -> { }).toEntity(String.class);
			ContratApi.verifier(CONTRAT, operation.id(), reponse);
			assertThat(reponse.getStatusCode().value()).as(operation.id()).isEqualTo(403);
			assertThat(json.readTree(reponse.getBody()).path("code").asString()).as(operation.id())
					.isEqualTo("CSRF_INVALIDE");
		}
	}

	/** Une mutation ajoutée plus tard sous /api/soirees est protégée avant même d'entrer au contrat. */
	@Test
	void uneMutationHorsDuContratSousLesSoireesExigeAussiLeCsrf() throws Exception {
		var session = SessionAnimateurHttp.ouvrir(port, json);
		for (var chemin : List.of("/api/soirees/{soireeId}/route-hors-contrat",
				"/api/soirees/{soireeId}/manches/{mancheId}/route-hors-contrat")) {
			for (var methode : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
				var reponse = envoyerSansCsrf(session, methode, chemin);
				assertThat(reponse.getStatusCode().value()).as("%s %s", methode, chemin).isEqualTo(403);
				assertThat(json.readTree(reponse.getBody()).path("code").asString()).as("%s %s", methode, chemin)
						.isEqualTo("CSRF_INVALIDE");
			}
		}
	}

	/** Les opérations publiques du contrat (security: []) restent ouvertes à un navigateur connecté. */
	@Test
	void lesOperationsPubliquesDesSoireesNeDemandentPasDeCsrf() throws Exception {
		var session = SessionAnimateurHttp.ouvrir(port, json);
		var operations = mutations(operation -> operation.has("security") && operation.path("security").isEmpty());
		assertThat(operations).extracting(Operation::id).contains("rejoindreSoiree");
		for (var operation : operations) {
			var reponse = envoyerSansCsrf(session, operation.methode(), operation.chemin());
			ContratApi.verifier(CONTRAT, operation.id(), reponse);
			assertThat(reponse.getBody() == null ? "" : json.readTree(reponse.getBody()).path("code").asString())
					.as(operation.id()).isNotEqualTo("CSRF_INVALIDE");
		}
	}

	private ResponseEntity<String> envoyerSansCsrf(SessionAnimateurHttp session, HttpMethod methode, String chemin) {
		return RestClient.create("http://localhost:" + port).method(methode)
				.uri(chemin.replaceAll("\\{[^}]+}", UUID.randomUUID().toString()))
				.header(HttpHeaders.COOKIE, session.cookie())
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON).body("{}")
				.retrieve().onStatus(statut -> statut.isError(), (demande, resultat) -> { }).toEntity(String.class);
	}

	private static List<Operation> mutationsDeSession() throws Exception {
		return mutations(operation -> operation.path("security").findValues("sessionAnimateur").size() > 0);
	}

	private static List<Operation> mutations(Predicate<JsonNode> retenue) throws Exception {
		JsonNode contrat = YAMLMapper.builder().build().readTree(Path.of(CONTRAT).toFile());
		var operations = new ArrayList<Operation>();
		for (var chemin : contrat.path("paths").properties()) {
			if (!chemin.getKey().startsWith("/api/soirees")) {
				continue; // Les routes du compte ont leurs propres tests.
			}
			for (var entree : chemin.getValue().properties()) {
				String methode = entree.getKey().toUpperCase(Locale.ROOT);
				if (List.of("POST", "PUT", "PATCH", "DELETE").contains(methode) && retenue.test(entree.getValue())) {
					operations.add(new Operation(entree.getValue().path("operationId").asString(),
							HttpMethod.valueOf(methode), chemin.getKey()));
				}
			}
		}
		return operations;
	}

	private record Operation(String id, HttpMethod methode, String chemin) {
	}
}
