package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
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

	private final List<StompSession> sessions = new CopyOnWriteArrayList<>();

	@AfterEach
	void fermerLesSessions() {
		sessions.forEach(StompSession::disconnect);
	}

	@Test
	void unBuzzDeLaManetteArriveSurLEcranDeSalleEnMoinsDUneSeconde() throws Exception {
		BlockingQueue<Map<String, Object>> ecran = abonner(connecter(), Canaux.salle(SOIREE));

		connecter().send("/app/soirees/" + SOIREE + "/buzz", Map.of("pseudo", "  Léa  "));

		Map<String, Object> message = ecran.poll(1, TimeUnit.SECONDS);
		assertThat(message).isNotNull().containsEntry("type", "buzz-recu");
		assertThat(donnees(message)).containsEntry("pseudo", "Léa").containsKey("recuLe");
	}

	@Test
	void unBuzzNeSortPasDeSaSoiree() throws Exception {
		BlockingQueue<Map<String, Object>> ecranDeLaSoiree = abonner(connecter(), Canaux.salle(SOIREE));
		BlockingQueue<Map<String, Object>> ecranDUneAutreSoiree = abonner(connecter(), Canaux.salle(AUTRE_SOIREE));

		connecter().send("/app/soirees/" + SOIREE + "/buzz", Map.of("pseudo", "Léa"));

		assertThat(ecranDeLaSoiree.poll(1, TimeUnit.SECONDS)).isNotNull();
		assertThat(ecranDUneAutreSoiree.poll(300, TimeUnit.MILLISECONDS)).isNull();
	}

	@Test
	void unBuzzSansPseudoEstIgnore() throws Exception {
		BlockingQueue<Map<String, Object>> ecran = abonner(connecter(), Canaux.salle(SOIREE));

		connecter().send("/app/soirees/" + SOIREE + "/buzz", Map.of("pseudo", "   "));

		assertThat(ecran.poll(300, TimeUnit.MILLISECONDS)).isNull();
	}

	@Test
	void leDiffuseurJointLaSeuleManetteVisee() throws Exception {
		UUID joueur = UUID.randomUUID();
		StompSession session = connecter();
		BlockingQueue<Map<String, Object>> manette = abonner(session, Canaux.joueur(joueur));
		BlockingQueue<Map<String, Object>> autreManette = abonner(session, Canaux.joueur(UUID.randomUUID()));

		diffuseur.envoyerJoueur(joueur, new EvenementDeTest("à toi de chanter"));

		Map<String, Object> message = manette.poll(1, TimeUnit.SECONDS);
		assertThat(message).isNotNull().containsEntry("type", "test");
		assertThat(donnees(message)).containsEntry("texte", "à toi de chanter");
		assertThat(autreManette.poll(300, TimeUnit.MILLISECONDS)).isNull();
	}

	@Test
	void leDiffuseurJointLaConsoleSansPasserParLaSalle() throws Exception {
		StompSession session = connecter();
		BlockingQueue<Map<String, Object>> console = abonner(session, Canaux.console(SOIREE));
		BlockingQueue<Map<String, Object>> salle = abonner(session, Canaux.salle(SOIREE));

		diffuseur.envoyerConsole(SOIREE, new EvenementDeTest("Joueur exclu"));

		assertThat(console.poll(1, TimeUnit.SECONDS)).isNotNull().containsEntry("type", "test");
		assertThat(salle.poll(300, TimeUnit.MILLISECONDS)).isNull();
	}

	private StompSession connecter() throws Exception {
		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		client.setMessageConverter(new JacksonJsonMessageConverter());
		StompSession session = client
				.connectAsync("ws://localhost:" + port + ConfigurationTempsReel.POINT_DE_CONNEXION,
						new StompSessionHandlerAdapter() {
						})
				.get(5, TimeUnit.SECONDS);
		sessions.add(session);
		return session;
	}

	/**
	 * S'abonne, puis attend la réponse de {@code /app/heure} : les messages d'une page
	 * étant traités dans l'ordre, l'abonnement est alors enregistré par le serveur.
	 */
	private BlockingQueue<Map<String, Object>> abonner(StompSession session, String destination) throws Exception {
		BlockingQueue<Map<String, Object>> recus = new ArrayBlockingQueue<>(10);
		session.subscribe(destination, collecteur(recus));
		BlockingQueue<Map<String, Object>> heure = new ArrayBlockingQueue<>(1);
		StompSession.Subscription sonde = session.subscribe("/app/heure", collecteur(heure));
		assertThat(heure.poll(2, TimeUnit.SECONDS)).as("heure du serveur").containsKey("maintenant");
		sonde.unsubscribe();
		return recus;
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
