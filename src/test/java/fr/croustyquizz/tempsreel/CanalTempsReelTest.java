package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.net.URI;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import static org.awaitility.Awaitility.await;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import fr.croustyquizz.TestcontainersConfiguration;

/** De bout en bout : de vrais clients STOMP contre l'application démarrée. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
@TestInstance(Lifecycle.PER_CLASS)
class CanalTempsReelTest {

	private static final UUID SOIREE = UUID.randomUUID();
	private static final UUID AUTRE_SOIREE = UUID.randomUUID();

	record EvenementDeTest(String texte) implements Evenement {
		@Override
		public String type() {
			return "test";
		}
	}

	@LocalServerPort
	private int port;

	@Autowired
	private DiffuseurTempsReel diffuseur;

	@Autowired
	@Qualifier("simpleBrokerMessageHandler")
	private AbstractBrokerMessageHandler broker;

	private final HttpClient http = HttpClient.newHttpClient();
	private final JsonMapper json = JsonMapper.builder().build();
	private final WsWebSocketContainer conteneur = new WsWebSocketContainer();
	private final SimpleAsyncTaskExecutor executeur = new SimpleAsyncTaskExecutor("stomp-test-");
	private final List<WebSocketStompClient> clients = new ArrayList<>();
	private final List<StompSession> sessions = new CopyOnWriteArrayList<>();

	@AfterEach
	void fermerLesSessions() {
		sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
		sessions.clear();
		clients.forEach(WebSocketStompClient::stop);
		clients.clear();
	}

	@AfterAll
	void fermerHttp() {
		http.close();
		executeur.close();
		conteneur.destroy();
	}

	@Test
	void unBuzzDeLaManetteArriveSurLEcranDeSalleEnMoinsDUneSeconde() throws Exception {
		BlockingQueue<Map<String, Object>> ecran = abonner(connecter(), Canaux.salle(SOIREE));

		BlockingQueue<Map<String, Object>> autreEcran = abonner(connecter(), Canaux.salle(SOIREE));
		long debut = System.nanoTime();
		HttpResponse<String> reponse = buzzer(SOIREE, "  Léa  ");
		assertThat(reponse.statusCode()).isEqualTo(200);

		Map<String, Object> message = ecran.poll(1, TimeUnit.SECONDS);
		assertThat(message).isNotNull().containsEntry("type", "buzz-recu");
		assertThat(donnees(message)).containsEntry("pseudo", "Léa").containsKey("recuLe");
		assertThat(autreEcran.poll(1, TimeUnit.SECONDS)).isEqualTo(message);
		assertThat(Duration.ofNanos(System.nanoTime() - debut)).isLessThan(Duration.ofSeconds(1));
		assertThat(json.readValue(reponse.body(), Map.class)).isEqualTo(donnees(message));
	}

	@Test
	void unBuzzNeSortPasDeSaSoiree() throws Exception {
		BlockingQueue<Map<String, Object>> ecranDeLaSoiree = abonner(connecter(), Canaux.salle(SOIREE));
		BlockingQueue<Map<String, Object>> ecranDUneAutreSoiree = abonner(connecter(), Canaux.salle(AUTRE_SOIREE));

		assertThat(buzzer(SOIREE, "Léa").statusCode()).isEqualTo(200);

		assertThat(ecranDeLaSoiree.poll(1, TimeUnit.SECONDS)).isNotNull();
		buzzer(AUTRE_SOIREE, "Autre");
		assertThat(donnees(ecranDUneAutreSoiree.poll(1, TimeUnit.SECONDS))).containsEntry("pseudo", "Autre");
		assertThat(ecranDUneAutreSoiree).isEmpty();
	}

	@Test
	void unBuzzSansPseudoEstRefuse() throws Exception {
		BlockingQueue<Map<String, Object>> ecran = abonner(connecter(), Canaux.salle(SOIREE));

		assertThat(buzzer(SOIREE, "   ").statusCode()).isEqualTo(400);
		buzzer(SOIREE, "Valide");
		assertThat(donnees(ecran.poll(1, TimeUnit.SECONDS))).containsEntry("pseudo", "Valide");
		assertThat(ecran).isEmpty();
	}

	@Test
	void leDiffuseurJointLaSeuleManetteVisee() throws Exception {
		UUID joueur = UUID.randomUUID();
		StompSession session = connecter();
		BlockingQueue<Map<String, Object>> manette = abonner(session, Canaux.joueur(joueur));
		BlockingQueue<Map<String, Object>> autreManette = abonner(session, Canaux.joueur(UUID.fromString("00000000-0000-0000-0000-000000000001")));

		diffuseur.envoyerJoueur(joueur, new EvenementDeTest("à toi de chanter"));

		Map<String, Object> message = manette.poll(1, TimeUnit.SECONDS);
		assertThat(message).isNotNull().containsEntry("type", "test");
		assertThat(donnees(message)).containsEntry("texte", "à toi de chanter");
		diffuseur.envoyerJoueur(UUID.fromString("00000000-0000-0000-0000-000000000001"), new EvenementDeTest("témoin"));
		assertThat(donnees(autreManette.poll(1, TimeUnit.SECONDS))).containsEntry("texte", "témoin");
		assertThat(autreManette).isEmpty();
	}

	@Test
	void leDiffuseurJointLaConsoleSansPasserParLaSalle() throws Exception {
		StompSession session = connecter();
		BlockingQueue<Map<String, Object>> console = abonner(session, Canaux.console(SOIREE));
		BlockingQueue<Map<String, Object>> salle = abonner(session, Canaux.salle(SOIREE));

		diffuseur.envoyerConsole(SOIREE, new EvenementDeTest("Joueur exclu"));

		assertThat(console.poll(1, TimeUnit.SECONDS)).isNotNull().containsEntry("type", "test");
		diffuseur.diffuserSalle(SOIREE, new EvenementDeTest("témoin"));
		assertThat(donnees(salle.poll(1, TimeUnit.SECONDS))).containsEntry("texte", "témoin");
		assertThat(salle).isEmpty();
	}

	private StandardWebSocketClient clientWebSocket() {
		StandardWebSocketClient client = new StandardWebSocketClient(conteneur);
		client.setTaskExecutor(executeur);
		return client;
	}

	private StompSession connecter() throws Exception {
		WebSocketStompClient client = new WebSocketStompClient(clientWebSocket());
		clients.add(client);
		client.setMessageConverter(new JacksonJsonMessageConverter());
		StompSession session = client
				.connectAsync("ws://localhost:" + port + ConfigurationTempsReel.POINT_DE_CONNEXION,
						new StompSessionHandlerAdapter() {
						})
				.get(5, TimeUnit.SECONDS);
		sessions.add(session);
		return session;
	}

	/** Attend que le broker ait enregistré cet identifiant d'abonnement. */
	private BlockingQueue<Map<String, Object>> abonner(StompSession session, String destination) {
		BlockingQueue<Map<String, Object>> recus = new ArrayBlockingQueue<>(64);
		StompHeaders entetes = new StompHeaders();
		entetes.setDestination(destination);
		String identifiant = UUID.randomUUID().toString();
		entetes.setId(identifiant);
		session.subscribe(entetes, collecteur(recus));
		SimpMessageHeaderAccessor sonde = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
		sonde.setDestination(destination);
		var message = MessageBuilder.createMessage(new byte[0], sonde.getMessageHeaders());
		await().atMost(Duration.ofSeconds(2)).until(() -> ((SimpleBrokerMessageHandler) broker)
				.getSubscriptionRegistry().findSubscriptions(message).values().stream()
				.anyMatch(ids -> ids.contains(identifiant)));
		return recus;
	}

	private HttpRequest requete(UUID soireeId, String pseudo) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/test/soirees/" + soireeId + "/buzz"))
				.timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("pseudo", pseudo)))).build();
	}

	private HttpResponse<String> buzzer(UUID soireeId, String pseudo) throws Exception {
		return http.send(requete(soireeId, pseudo), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void lesBuzzConcurrentsSontDiffusesDansLOrdreDeLeurHorodatage() throws Exception {
		BlockingQueue<Map<String, Object>> ecran = abonner(connecter(), Canaux.salle(SOIREE));
		List<CompletableFuture<HttpResponse<String>>> envois = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			envois.add(http.sendAsync(requete(SOIREE, "Joueur " + i), HttpResponse.BodyHandlers.ofString()));
		}
		for (var envoi : envois) assertThat(envoi.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		List<Instant> instants = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			Map<String, Object> message = ecran.poll(1, TimeUnit.SECONDS);
			assertThat(message).isNotNull();
			instants.add(Instant.parse((String) donnees(message).get("recuLe")));
		}
		assertThat(instants).isSorted();
	}

	@Test
	void lePseudoTronqueConserveLEmojiEntier() throws Exception {
		HttpResponse<String> reponse = buzzer(SOIREE, "a".repeat(29) + "😀z");
		assertThat(reponse.statusCode()).isEqualTo(200);
		assertThat(json.readValue(reponse.body(), Map.class)).containsEntry("pseudo", "a".repeat(29) + "😀");
	}

	@Test
	void lHeureEtLesCanauxSeConsultentParHttp() throws Exception {
		var heure = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/heure")).build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(heure.statusCode()).isEqualTo(200);
		assertThat(json.readValue(heure.body(), Map.class)).containsKey("maintenant");
		var canaux = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/test/soirees/" + SOIREE + "/canaux")).build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(canaux.statusCode()).isEqualTo(200);
		assertThat(json.readValue(canaux.body(), Map.class)).containsEntry("salle", Canaux.salle(SOIREE));
	}

	private static StompFrameHandler collecteur(BlockingQueue<Map<String, Object>> recus) {
		return new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders entetes) {
				return Map.class;
			}

			@Override
			@SuppressWarnings("unchecked")
			public void handleFrame(StompHeaders entetes, Object contenu) {
				recus.add((Map<String, Object>) contenu);
			}
		};
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> donnees(Map<String, Object> message) {
		return (Map<String, Object>) message.get("donnees");
	}

}
