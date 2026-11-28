package fr.croustyquizz.tempsreel;

import static fr.croustyquizz.Attentes.PLAFOND;
import static fr.croustyquizz.Attentes.PLAFOND_SECONDES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.Type;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import fr.croustyquizz.ContratApi;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Client HTTP et STOMP d'une Soirée enregistrée, partagé par les tests qui la jouent de bout en bout : ouverture,
 * arrivée des Joueurs, abonnements prêts à recevoir, réponses vérifiées contre le contrat, et rafale d'envois
 * simultanés pour les tests à soixante Manettes. Une instance par test, fermée après.
 */
public final class ClientDeSoiree implements AutoCloseable {

	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final String SOIREES = "/api/soirees";
	/**
	 * Délai de lecture d'une réponse HTTP. La rafale attend chaque réponse une seconde de plus, au-delà de
	 * {@code Attentes.PLAFOND} : une réponse trop lente échoue alors par le client HTTP, avec son motif.
	 */
	private static final Duration LECTURE = Duration.ofSeconds(15);

	private final int port;
	private final JsonMapper json;
	private final WsWebSocketContainer conteneur = new WsWebSocketContainer();
	private final WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient(conteneur));
	private final List<StompSession> sessions = new ArrayList<>();
	private final HttpClient clientHttp = HttpClient.newBuilder().connectTimeout(PLAFOND).build();
	private final RestClient http;

	public ClientDeSoiree(int port, JsonMapper json) {
		this.port = port;
		this.json = json;
		var fabrique = new JdkClientHttpRequestFactory(clientHttp);
		fabrique.setReadTimeout(LECTURE);
		this.http = RestClient.builder().requestFactory(fabrique).build();
		stomp.setMessageConverter(new JacksonJsonMessageConverter());
	}

	@Override
	public void close() {
		for (var session : sessions) {
			if (session.isConnected()) {
				session.disconnect();
			}
		}
		stomp.stop();
		conteneur.destroy();
		clientHttp.close();
	}

	/** Soirée SOLO en pilotage manuel, ouverte, avec un accès d'Écran de salle. */
	public SoireeOuverte ouvrirSoiree() throws Exception {
		JsonNode creation = verifier("creerSoiree", envoyer(HttpMethod.POST, SOIREES, null,
				Map.of("titre", "Soirée du vendredi", "format", "SOLO", "pilotage", "MANUEL"), null), 201);
		UUID id = UUID.fromString(creation.path("id").asString());
		String animateur = "demo:ANIMATEUR:" + id + ":" + UUID.randomUUID();
		String route = SOIREES + "/" + id;
		JsonNode ouverte = verifier("ouvrirSoiree", envoyer(HttpMethod.POST, route + "/ouverture", animateur,
				null, UUID.randomUUID()), 200);
		JsonNode ecran = verifier("creerAccesEcran", envoyer(HttpMethod.POST, route + "/acces-ecran", animateur,
				null, UUID.randomUUID()), 200);
		return new SoireeOuverte(id, route, ouverte.path("codeAcces").asString(), animateur, ecran.path("cleEcran").asString());
	}

	public JoueurArrive rejoindre(SoireeOuverte soiree, String pseudo) throws Exception {
		JsonNode arrivee = verifier("rejoindreSoiree", envoyer(HttpMethod.POST, SOIREES + "/rejoindre", null,
				Map.of("codeAcces", soiree.code(), "pseudo", pseudo), null), 201);
		return new JoueurArrive(UUID.fromString(arrivee.path("joueur").path("id").asString()),
				arrivee.path("jetonReconnexion").asString());
	}

	public BlockingQueue<JsonNode> abonner(SoireeOuverte soiree, String ecran, String secret, String destination)
			throws Exception {
		return abonner(soiree, ecran, secret, destination, Function.identity());
	}

	/**
	 * Abonne une session STOMP à la destination et rend la main quand la capture de cet abonnement répond 200,
	 * c'est-à-dire quand l'abonnement est installé. La file n'a pas de borne : un message ne s'y perd jamais.
	 */
	public <T> BlockingQueue<T> abonner(SoireeOuverte soiree, String ecran, String secret, String destination,
			Function<JsonNode, T> projection) throws Exception {
		StompHeaders connexion = new StompHeaders();
		connexion.set("Authorization", "Bearer " + secret);
		StompSession session = stomp.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
				connexion, new StompSessionHandlerAdapter() { }).get(PLAFOND_SECONDES, TimeUnit.SECONDS);
		sessions.add(session);
		StompHeaders abonnement = new StompHeaders();
		abonnement.setDestination(destination);
		abonnement.setId(UUID.randomUUID().toString());
		BlockingQueue<T> recus = new LinkedBlockingQueue<>();
		session.subscribe(abonnement, new StompFrameHandler() {
			@Override public Type getPayloadType(StompHeaders entetes) { return JsonNode.class; }
			@Override public void handleFrame(StompHeaders entetes, Object contenu) { recus.add(projection.apply((JsonNode) contenu)); }
		});
		String capture = soiree.route() + "/etat/" + ecran + "?abonnementId=" + abonnement.getId();
		await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(10)).atMost(PLAFOND)
				.until(() -> envoyer(HttpMethod.GET, capture, secret, null, null)
				.getStatusCode().value() == 200);
		return recus;
	}

	/**
	 * Lance tous les envois en même temps, un fil virtuel chacun, et rend leurs réponses dans l'ordre des envois.
	 * Les fils attendent ensemble le départ : la durée commence au départ et finit à la dernière réponse.
	 */
	public static <T> Rafale enRafale(List<T> envois, Function<T, ResponseEntity<String>> envoi) throws Exception {
		CountDownLatch prets = new CountDownLatch(envois.size());
		CountDownLatch depart = new CountDownLatch(1);
		try (var executeur = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<ResponseEntity<String>>> attendues = new ArrayList<>();
			for (var element : envois) {
				attendues.add(executeur.submit(() -> {
					prets.countDown();
					if (!depart.await(PLAFOND_SECONDES, TimeUnit.SECONDS)) { throw new IllegalStateException("Départ de la rafale manqué."); }
					return envoi.apply(element);
				}));
			}
			assertThat(prets.await(PLAFOND_SECONDES, TimeUnit.SECONDS)).isTrue();
			long debut = System.nanoTime();
			depart.countDown();
			List<ResponseEntity<String>> reponses = new ArrayList<>();
			for (var attendue : attendues) {
				reponses.add(attendue.get(LECTURE.toSeconds() + 1, TimeUnit.SECONDS));
			}
			return new Rafale(reponses, Duration.ofNanos(System.nanoTime() - debut));
		}
	}

	public ResponseEntity<String> envoyer(HttpMethod methode, String route, String secret, Object corps, UUID cle) {
		var requete = http.method(methode).uri("http://localhost:" + port + route);
		if (secret != null) { requete.headers(entetes -> entetes.setBearerAuth(secret)); }
		if (cle != null) { requete.header("Idempotency-Key", cle.toString()).header("X-CSRF-TOKEN", "demonstration"); }
		if (corps != null) { requete.contentType(MediaType.APPLICATION_JSON).body(corps); }
		return requete.retrieve().onStatus(statut -> statut.isError(), (q, r) -> { }).toEntity(String.class);
	}

	/** Vérifie le statut et la réponse contre l'opération du contrat, puis rend le corps lu. */
	public JsonNode verifier(String operation, ResponseEntity<String> reponse, int statut) throws Exception {
		assertThat(reponse.getStatusCode().value()).as("%s : %s", operation, reponse.getBody()).isEqualTo(statut);
		ContratApi.verifier(CONTRAT, operation, reponse);
		return json.readTree(reponse.getBody());
	}

	public record SoireeOuverte(UUID id, String route, String code, String animateur, String cleEcran) { }
	public record JoueurArrive(UUID id, String jeton) { }
	/** Réponses d'une rafale dans l'ordre des envois, et sa durée du départ à la dernière réponse. */
	public record Rafale(List<ResponseEntity<String>> reponses, Duration duree) { }
}
