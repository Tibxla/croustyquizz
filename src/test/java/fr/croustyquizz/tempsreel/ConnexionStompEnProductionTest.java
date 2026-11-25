package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import fr.croustyquizz.SessionAnimateurHttp;
import fr.croustyquizz.TestcontainersConfiguration;
import tools.jackson.databind.json.JsonMapper;

/**
 * En production, le cookie de session sans jeton CSRF ne donne aucune identité au CONNECT : sans Bearer,
 * la connexion n'est pas acceptée. En dev et recette, la même connexion reste anonyme ({@link CanalTempsReelTest}).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("prod")
class ConnexionStompEnProductionTest {

	@LocalServerPort
	private int port;
	@Autowired
	private JsonMapper json;

	@Test
	void leCookieSansJetonCsrfNOuvrePasDeConnexion() throws Exception {
		assertThat(premiereTrame(SessionAnimateurHttp.ouvrir(port, json).cookie(), "")).satisfiesAnyOf(
				trame -> assertThat(trame).isNull(), trame -> assertThat(trame).doesNotStartWith("CONNECTED"));
	}

	@Test
	void leCookieAvecSonJetonCsrfOuvreLaConnexionDeLaConsole() throws Exception {
		var session = SessionAnimateurHttp.ouvrir(port, json);
		assertThat(premiereTrame(session.cookie(), "X-CSRF-TOKEN:" + session.csrf() + "\n")).startsWith("CONNECTED");
	}

	private String premiereTrame(String cookie, String entetes) throws Exception {
		var trames = new LinkedBlockingQueue<String>();
		try (var client = HttpClient.newHttpClient()) {
			WebSocket socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
					.subprotocols("v12.stomp").header("Cookie", cookie)
					.buildAsync(URI.create("ws://localhost:" + port + ConfigurationTempsReel.POINT_DE_CONNEXION),
							new WebSocket.Listener() {
								private final StringBuilder trame = new StringBuilder();

								@Override
								public CompletionStage<?> onText(WebSocket ws, CharSequence fragment, boolean fin) {
									trame.append(fragment);
									if (fin) {
										trames.add(trame.toString());
										trame.setLength(0);
									}
									return WebSocket.Listener.super.onText(ws, fragment, fin);
								}
							}).get(5, TimeUnit.SECONDS);
			try {
				socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\n" + entetes + "\n\0", true).join();
				return trames.poll(2, TimeUnit.SECONDS);
			} finally {
				socket.abort();
			}
		}
	}
}
