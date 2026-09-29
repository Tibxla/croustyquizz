package fr.croustyquizz;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DemarrageApplicationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@LocalServerPort
	private int port;

	@Test
	void appliqueToutesLesMigrationsSurUnPostgresVierge() {
		Integer echecs = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where not success", Integer.class);
		Integer appliquees = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where success", Integer.class);

		assertThat(echecs).isZero();
		assertThat(appliquees).isPositive();
	}

	@Test
	void sertLaPageDAccueil() throws Exception {
		HttpResponse<String> reponse = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(reponse.statusCode()).isEqualTo(200);
		assertThat(reponse.body()).contains("<title>CroustyQuizz</title>");
	}

}
