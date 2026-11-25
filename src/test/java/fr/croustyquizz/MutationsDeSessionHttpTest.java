package fr.croustyquizz;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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

	private static List<Operation> mutationsDeSession() throws Exception {
		JsonNode contrat = YAMLMapper.builder().build().readTree(Path.of(CONTRAT).toFile());
		var operations = new ArrayList<Operation>();
		for (var chemin : contrat.path("paths").properties()) {
			if (!chemin.getKey().startsWith("/api/soirees")) {
				continue; // Les routes du compte ont leurs propres tests.
			}
			for (var entree : chemin.getValue().properties()) {
				String methode = entree.getKey().toUpperCase(Locale.ROOT);
				if (List.of("POST", "PUT", "PATCH", "DELETE").contains(methode)
						&& entree.getValue().path("security").findValues("sessionAnimateur").size() > 0) {
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
