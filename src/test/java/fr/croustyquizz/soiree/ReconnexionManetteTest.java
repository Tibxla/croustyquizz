package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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
import fr.croustyquizz.SessionAnimateurHttp;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.tempsreel.AssembleurDeVues;
import fr.croustyquizz.tempsreel.Canaux;
import fr.croustyquizz.tempsreel.DiffuseurTempsReel;
import fr.croustyquizz.tempsreel.RegistreAbonnements;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reconnexion d'une Manette avec son vrai jeton (#10) : STOMP et capture HTTP
 * authentifiés par le jeton, barrière d'abonnement et Révisions de tempsreel.
 * Les vues sont publiées comme le fait PublicationDeVues après une commande :
 * assemblées par AssembleurDeVues puis envoyées par le DiffuseurTempsReel.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ReconnexionManetteTest {

	private static final String CONTRAT = "docs/api/openapi.yaml";

	@LocalServerPort
	private int port;

	/** Animateur du Lieu qui crée et ouvre les Soirées : ses requêtes partent sans Bearer, avec sa session. */
	private SessionAnimateurHttp proprietaire;

	@Autowired
	private JsonMapper json;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AssembleurDeVues assembleur;

	@Autowired
	private DiffuseurTempsReel diffuseur;

	@Autowired
	private RegistreAbonnements abonnements;

	private final WsWebSocketContainer conteneur = new WsWebSocketContainer();
	private final SimpleAsyncTaskExecutor executeur = new SimpleAsyncTaskExecutor("manette-test-");
	private final List<WebSocketStompClient> clients = new ArrayList<>();

	@BeforeEach
	void connecterLeProprietaire() throws Exception {
		proprietaire = SessionAnimateurHttp.ouvrir(port, json);
	}

	@AfterEach
	void toutFermer() {
		clients.forEach(WebSocketStompClient::stop);
		clients.clear();
		executeur.close();
		jdbc.update("delete from acces_ecran");
		jdbc.update("delete from joueur");
		jdbc.update("delete from soiree");
	}

	@Test
	void uneManetteReconnecteeRetrouveSaVueSansTrouNiRejeu() throws Exception {
		JsonNode soiree = soireeOuverte();
		UUID soireeId = UUID.fromString(soiree.path("id").asString());
		JsonNode arrivee = rejoindre(soiree.path("codeAcces").asString(), "Léa");
		String jeton = arrivee.path("jetonReconnexion").asString();
		UUID joueurId = UUID.fromString(arrivee.at("/joueur/id").asString());
		Acteur lea = new Acteur(Acteur.Type.JOUEUR, joueurId, soireeId);

		// Première connexion : abonnement, puis capture derrière la barrière.
		StompSession premiere = connecter(jeton);
		UUID abonnement = UUID.randomUUID();
		BlockingQueue<Map<String, Object>> recusAvant = abonner(premiere, Canaux.joueur(joueurId), abonnement, lea);
		JsonNode capture = capturer(soireeId, abonnement, jeton);
		long revision = capture.path("revision").asLong();
		publier(soireeId, joueurId);
		assertThat(revisionDe(recusAvant.poll(5, TimeUnit.SECONDS))).isEqualTo(revision + 1);

		// La Manette se met en veille : une vue part pendant qu'elle n'écoute plus.
		premiere.disconnect();
		await().atMost(Duration.ofSeconds(5)).until(() -> !abonnements.estInstalle(abonnement, Canaux.joueur(joueurId), lea));
		// Sa seule connexion fermée, Léa passe hors ligne (#8) : une vue de plus part vers les Manettes.
		await().atMost(Duration.ofSeconds(5)).until(() -> !json.readTree(envoyer(HttpMethod.GET,
				"/api/soirees/" + soireeId + "/etat/manette", null, jeton).getBody()).at("/vue/joueurs/0/connecte").asBoolean());
		publier(soireeId, joueurId);

		// Reconnexion avec le même jeton, qui la remet en ligne avant CONNECTED ; une vue part pendant la resynchronisation.
		StompSession seconde = connecter(jeton);
		UUID reprise = UUID.randomUUID();
		BlockingQueue<Map<String, Object>> recus = abonner(seconde, Canaux.joueur(joueurId), reprise, lea);
		publier(soireeId, joueurId);
		Map<String, Object> pendantLaReprise = recus.poll(5, TimeUnit.SECONDS);
		JsonNode recapture = capturer(soireeId, reprise, jeton);

		long revisionReprise = recapture.path("revision").asLong();
		// Trois vues publiées par le test, plus le départ hors ligne et le retour en ligne.
		assertThat(revisionReprise).isEqualTo(revision + 5);
		assertThat(recapture.at("/vue/joueurs/0/connecte").asBoolean()).isTrue();
		assertThat(recapture.at("/vue/joueurId").asString()).isEqualTo(joueurId.toString());
		assertThat(recapture.at("/vue/joueurs/0/pseudo").asString()).isEqualTo("Léa");
		// La vue manquée n'est pas rejouée ; celle reçue pendant la reprise est déjà dans la capture.
		assertThat(revisionDe(pendantLaReprise)).isEqualTo(revisionReprise);
		assertThat(recus).isEmpty();

		publier(soireeId, joueurId);
		assertThat(revisionDe(recus.poll(5, TimeUnit.SECONDS))).isEqualTo(revisionReprise + 1);
	}

	@Test
	void leJetonDonneLaVueDeSonJoueurEtPlusRienApresLExclusion() throws Exception {
		JsonNode soiree = soireeOuverte();
		String soireeId = soiree.path("id").asString();
		String code = soiree.path("codeAcces").asString();
		JsonNode lea = rejoindre(code, "Léa");
		JsonNode tom = rejoindre(code, "Tom");
		String route = "/api/soirees/" + soireeId + "/etat/manette";

		var vueDeTom = envoyer(HttpMethod.GET, route, null, tom.path("jetonReconnexion").asString());
		ContratApi.verifier(CONTRAT, "retrouverEtatManette", vueDeTom);
		assertThat(json.readTree(vueDeTom.getBody()).at("/vue/joueurId").asString())
				.isEqualTo(tom.at("/joueur/id").asString());
		// Sans Mode de jeu qui les fournisse, ni classement ni Réponse.
		assertThat(json.readTree(vueDeTom.getBody()).at("/vue/classement").isEmpty()).isTrue();
		assertThat(json.readTree(vueDeTom.getBody()).at("/vue/derniereReponse").isNull()).isTrue();
		assertThat(json.readTree(vueDeTom.getBody()).at("/vue/resultatPersonnel").isNull()).isTrue();

		jdbc.update("update joueur set exclu = true where id = ?::uuid", lea.at("/joueur/id").asString());
		var apresExclusion = envoyer(HttpMethod.GET, route, null, lea.path("jetonReconnexion").asString());
		ContratApi.verifier(CONTRAT, "retrouverEtatManette", apresExclusion);
		assertThat(apresExclusion.getStatusCode().value()).isEqualTo(401);
	}

	/** Ce que fait PublicationDeVues pour une Manette : assembler sa vue complète, puis la diffuser. */
	private void publier(UUID soireeId, UUID joueurId) {
		diffuseur.envoyerJoueur(soireeId, joueurId,
				assembleur.assembler(soireeId, Destination.joueur(joueurId), "joueurs-modifies"));
	}

	private JsonNode capturer(UUID soireeId, UUID abonnement, String jeton) throws Exception {
		var reponse = envoyer(HttpMethod.GET,
				"/api/soirees/" + soireeId + "/etat/manette?abonnementId=" + abonnement, null, jeton);
		ContratApi.verifier(CONTRAT, "retrouverEtatManette", reponse);
		assertThat(reponse.getStatusCode().value()).isEqualTo(200);
		JsonNode corps = json.readTree(reponse.getBody());
		assertThat(corps.path("abonnementId").asString()).isEqualTo(abonnement.toString());
		return corps;
	}

	private StompSession connecter(String jeton) throws Exception {
		StandardWebSocketClient webSocket = new StandardWebSocketClient(conteneur);
		webSocket.setTaskExecutor(executeur);
		WebSocketStompClient client = new WebSocketStompClient(webSocket);
		clients.add(client);
		client.setMessageConverter(new JacksonJsonMessageConverter());
		StompHeaders entetes = new StompHeaders();
		entetes.set("Authorization", "Bearer " + jeton);
		return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), entetes,
				new StompSessionHandlerAdapter() {
				}).get(5, TimeUnit.SECONDS);
	}

	/** Attend que le broker ait installé l'abonnement, comme la barrière que vérifie la capture. */
	private BlockingQueue<Map<String, Object>> abonner(StompSession session, String destination, UUID abonnement,
			Acteur acteur) {
		BlockingQueue<Map<String, Object>> recus = new ArrayBlockingQueue<>(16);
		StompHeaders entetes = new StompHeaders();
		entetes.setDestination(destination);
		entetes.setId(abonnement.toString());
		session.subscribe(entetes, new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders entetesRecus) {
				return Map.class;
			}

			@Override
			@SuppressWarnings("unchecked")
			public void handleFrame(StompHeaders entetesRecus, Object contenu) {
				recus.add((Map<String, Object>) contenu);
			}
		});
		await().atMost(Duration.ofSeconds(5)).until(() -> abonnements.estInstalle(abonnement, destination, acteur));
		return recus;
	}

	private static long revisionDe(Map<String, Object> message) {
		assertThat(message).as("vue reçue sur le canal de la Manette").isNotNull();
		return ((Number) message.get("revision")).longValue();
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
		} else {
			requete.headers(entetes -> entetes.addAll(proprietaire.entetes()));
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
