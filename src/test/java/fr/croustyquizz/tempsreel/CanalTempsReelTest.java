package fr.croustyquizz.tempsreel;

import static fr.croustyquizz.Attentes.PLAFOND;
import static fr.croustyquizz.Attentes.PLAFOND_SECONDES;
import static fr.croustyquizz.Attentes.RETENUE_SECONDES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompDecoder;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.WebSocketHttpHeaders;

import tools.jackson.databind.json.JsonMapper;

import fr.croustyquizz.SessionAnimateurHttp;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.soiree.Format;
import fr.croustyquizz.soiree.Pilotage;
import fr.croustyquizz.soiree.ServiceDesAcces;
import fr.croustyquizz.soiree.ServiceSoiree;

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
	private RegistreAbonnements abonnements;

	@Autowired
	private ServiceSoiree soirees;

	@Autowired
	private ServiceDesAcces acces;

	@MockitoBean
	private CaptureVuePrototype repli;

	// Espion plutôt que simulacre : l'horloge réelle sert au démarrage du contexte (LimiteDeRequetes la lit déjà).
	@MockitoSpyBean
	private Clock horloge;

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
	void uneSoireeEnregistreeRefuseLAbonnementAnonymeAuCanalDeSalle() throws Exception {
		UUID soiree = soirees.creer("Soirée enregistrée", Format.SOLO, Pilotage.MANUEL).getId();
		String destination = Canaux.salle(soiree);
		UUID abonnement = UUID.randomUUID();
		var session = connecter();
		var recus = new ArrayBlockingQueue<Map<String, Object>>(2);
		var entetes = new StompHeaders();
		entetes.setId(abonnement.toString());
		entetes.setDestination(destination);
		session.subscribe(entetes, collecteur(recus));

		// Le broker traite les abonnements d'une même connexion dans l'ordre.
		UUID demonstration = UUID.randomUUID();
		var temoin = abonner(session, Canaux.salle(demonstration));
		assertThat(abonnements.estInstalle(abonnement, destination)).isFalse();
		diffuseur.diffuserSalle(soiree, new EvenementDeTest("Vue de la Soirée enregistrée"));
		diffuseur.diffuserSalle(demonstration, new EvenementDeTest("Vue de démonstration"));
		assertThat(temoin.poll(PLAFOND_SECONDES, TimeUnit.SECONDS)).isNotNull();
		assertThat(recus).isEmpty();
	}

	@Test
	void unConnectRefuseRecoitUneTrameErrorAvecSonMotifPuisLaFermeture() throws Exception {
		UUID soiree = soirees.creer("Soirée enregistrée", Format.SOLO, Pilotage.MANUEL).getId();
		assertThat(motifDuConnectRefuse("demo:ECRAN:" + soiree + ":" + UUID.randomUUID())).isEqualTo(
				"Cette Soirée n'accepte pas d'Écran de démonstration : ouvre l'Écran de salle avec la clé créée depuis la Console.");
	}

	@Test
	void uneCleDEcranExpireeInviteARouvrirLEcranDepuisLaPreparation() throws Exception {
		when(horloge.instant()).thenReturn(Instant.parse("2026-11-26T19:30:00Z"));
		UUID soiree = soirees.creer("Soirée avec Écran", Format.SOLO, Pilotage.MANUEL).getId();
		var ouverture = acces.ouvrirEcran(soiree);
		when(horloge.instant()).thenReturn(ouverture.acces().getExpireLe());
		String consigne = "Rouvre l'Écran de salle depuis la préparation de la Soirée pour obtenir une nouvelle clé";

		assertThat(motifDuConnectRefuse(ouverture.cle())).contains(consigne);
		acces.ouvrirEcran(soiree); // La création d'une clé purge les clés expirées.
		assertThat(motifDuConnectRefuse(ouverture.cle())).contains(consigne);
	}

	private String motifDuConnectRefuse(String jeton) throws Exception {
		return motifDuConnectRefuse(new WebSocketHttpHeaders(), "Authorization:Bearer " + jeton);
	}

	private String motifDuConnectRefuse(WebSocketHttpHeaders entetes, String enteteStomp) throws Exception {
		BlockingQueue<String> trames = new ArrayBlockingQueue<>(4);
		CompletableFuture<CloseStatus> fermeture = new CompletableFuture<>();
		entetes.setSecWebSocketProtocol("v12.stomp");
		WebSocketSession connexion = clientWebSocket().execute(new TextWebSocketHandler() {
			@Override
			protected void handleTextMessage(WebSocketSession session, TextMessage message) {
				trames.add(message.getPayload());
			}

			@Override
			public void afterConnectionClosed(WebSocketSession session, CloseStatus statut) {
				fermeture.complete(statut);
			}
		}, entetes, URI.create("ws://localhost:" + port + ConfigurationTempsReel.POINT_DE_CONNEXION))
				.get(PLAFOND_SECONDES, TimeUnit.SECONDS);
		try {
			connexion.sendMessage(new TextMessage("CONNECT\naccept-version:1.2\nhost:localhost\n" + enteteStomp + "\n\n\0"));

			String trame = trames.poll(PLAFOND_SECONDES, TimeUnit.SECONDS);
			assertThat(trame).as("réponse au CONNECT refusé").isNotNull();
			var erreur = StompHeaderAccessor.wrap(new StompDecoder()
					.decode(ByteBuffer.wrap(trame.getBytes(StandardCharsets.UTF_8))).getFirst());
			assertThat(erreur.getCommand()).isEqualTo(StompCommand.ERROR);
			assertThat(fermeture.get(PLAFOND_SECONDES, TimeUnit.SECONDS)).isEqualTo(CloseStatus.PROTOCOL_ERROR);
			assertThat(trames).isEmpty();
			return erreur.getMessage();
		} finally {
			if (connexion.isOpen()) {
				connexion.close();
			}
		}
	}

	@Test
	void laCleDEcranPermetDeRecevoirLesEvenementsDeLaSoireeEnregistree() throws Exception {
		UUID soiree = soirees.creer("Soirée avec Écran", Format.SOLO, Pilotage.MANUEL).getId();
		String cle = acces.ouvrirEcran(soiree).cle();
		var ecran = abonner(connecterAvecJeton(cle), Canaux.salle(soiree));

		diffuseur.diffuserSalle(soiree, new EvenementDeTest("Vue de la Soirée enregistrée"));

		assertThat(ecran.poll(PLAFOND_SECONDES, TimeUnit.SECONDS)).isNotNull().containsEntry("soireeId", soiree.toString());
	}

	@Test
	void unEcranAnonymeDeDemonstrationRecoitLeBuzzEnMoinsDUneSeconde() throws Exception {
		BlockingQueue<Map<String, Object>> ecran = abonner(connecter(), Canaux.salle(SOIREE));

		BlockingQueue<Map<String, Object>> autreEcran = abonner(connecter(), Canaux.salle(SOIREE));
		long debut = System.nanoTime();
		HttpResponse<String> reponse = buzzer(SOIREE, "  Léa  ");
		assertThat(reponse.statusCode()).isEqualTo(200);

		// La seconde est ici l'exigence du test, pas un plafond d'attente : elle reste fixe.
		Map<String, Object> message = ecran.poll(1, TimeUnit.SECONDS);
		assertThat(message).isNotNull().containsEntry("type", "buzz-recu");
		assertThat(dernierBuzz(message)).containsEntry("pseudo", "Léa").containsKey("recuLe");
		assertThat(autreEcran.poll(1, TimeUnit.SECONDS)).isEqualTo(message);
		assertThat(Duration.ofNanos(System.nanoTime() - debut)).isLessThan(Duration.ofSeconds(1));
		assertThat(json.readValue(reponse.body(), Map.class)).isEqualTo(dernierBuzz(message));
	}

	@Test
	void unBuzzNeSortPasDeSaSoiree() throws Exception {
		BlockingQueue<Map<String, Object>> ecranDeLaSoiree = abonner(connecter(), Canaux.salle(SOIREE));
		BlockingQueue<Map<String, Object>> ecranDUneAutreSoiree = abonner(connecter(), Canaux.salle(AUTRE_SOIREE));

		assertThat(buzzer(SOIREE, "Léa").statusCode()).isEqualTo(200);

		assertThat(ecranDeLaSoiree.poll(PLAFOND_SECONDES, TimeUnit.SECONDS)).isNotNull();
		buzzer(AUTRE_SOIREE, "Autre");
		assertThat(dernierBuzz(ecranDUneAutreSoiree.poll(PLAFOND_SECONDES, TimeUnit.SECONDS))).containsEntry("pseudo", "Autre");
		assertThat(ecranDUneAutreSoiree).isEmpty();
	}

	@Test
	void unBuzzSansPseudoEstRefuse() throws Exception {
		BlockingQueue<Map<String, Object>> ecran = abonner(connecter(), Canaux.salle(SOIREE));

		assertThat(buzzer(SOIREE, "   ").statusCode()).isEqualTo(400);
		buzzer(SOIREE, "Valide");
		assertThat(dernierBuzz(ecran.poll(PLAFOND_SECONDES, TimeUnit.SECONDS))).containsEntry("pseudo", "Valide");
		assertThat(ecran).isEmpty();
	}

	@Test
	void leDiffuseurJointLaSeuleManetteVisee() throws Exception {
		UUID soiree = UUID.randomUUID();
		UUID autreSoiree = UUID.randomUUID();
		UUID joueur = FournisseurCommunDemonstration.joueurId(soiree);
		UUID autreJoueur = FournisseurCommunDemonstration.joueurId(autreSoiree);
		BlockingQueue<Map<String, Object>> manette = abonner(connecter(new Acteur(Acteur.Type.JOUEUR, joueur, soiree)), Canaux.joueur(joueur));
		BlockingQueue<Map<String, Object>> autreManette = abonner(connecter(new Acteur(Acteur.Type.JOUEUR, autreJoueur, autreSoiree)),
				Canaux.joueur(autreJoueur));

		diffuseur.envoyerJoueur(soiree, joueur, new EvenementDeTest("à toi de chanter"));

		Map<String, Object> message = manette.poll(PLAFOND_SECONDES, TimeUnit.SECONDS);
		assertThat(message).isNotNull().containsEntry("type", "test");
		assertThat(donnees(message)).containsEntry("texte", "à toi de chanter");
		diffuseur.envoyerJoueur(autreSoiree, autreJoueur, new EvenementDeTest("témoin"));
		assertThat(donnees(autreManette.poll(PLAFOND_SECONDES, TimeUnit.SECONDS))).containsEntry("texte", "témoin");
		assertThat(autreManette).isEmpty();
	}

	@Test
	void deuxManettesDeDemonstrationDeLaMemeSoireeOntChacuneLeurCanalEtLeursRevisions() throws Exception {
		UUID soiree = UUID.randomUUID();
		UUID joueur2 = FournisseurCommunDemonstration.joueurId(soiree, 2);
		UUID joueur3 = FournisseurCommunDemonstration.joueurId(soiree, 3);
		var manette2 = abonner(connecter(new Acteur(Acteur.Type.JOUEUR, joueur2, soiree)), "/topic/joueurs/" + joueur2);
		var manette3 = abonner(connecter(new Acteur(Acteur.Type.JOUEUR, joueur3, soiree)), "/topic/joueurs/" + joueur3);

		diffuseur.envoyerJoueur(soiree, joueur2, new EvenementDeTest("pour le Joueur 2"));
		var premier = manette2.poll(PLAFOND_SECONDES, TimeUnit.SECONDS);
		assertThat(premier).isNotNull().containsEntry("revision", 1);
		assertThat(donnees(premier)).containsEntry("texte", "pour le Joueur 2");

		diffuseur.envoyerJoueur(soiree, joueur3, new EvenementDeTest("pour le Joueur 3"));
		var autre = manette3.poll(PLAFOND_SECONDES, TimeUnit.SECONDS);
		assertThat(autre).isNotNull().containsEntry("revision", 1);
		assertThat(donnees(autre)).containsEntry("texte", "pour le Joueur 3");

		diffuseur.envoyerJoueur(soiree, joueur2, new EvenementDeTest("encore pour le Joueur 2"));
		var suivant = manette2.poll(PLAFOND_SECONDES, TimeUnit.SECONDS);
		assertThat(suivant).isNotNull().containsEntry("revision", 2);
		assertThat(donnees(suivant)).containsEntry("texte", "encore pour le Joueur 2");
	}

	@Test
	void leDiffuseurJointLaConsoleSansPasserParLaSalle() throws Exception {
		StompSession session = connecter(new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), SOIREE));
		BlockingQueue<Map<String, Object>> console = abonner(session, Canaux.console(SOIREE));
		BlockingQueue<Map<String, Object>> salle = abonner(session, Canaux.salle(SOIREE));

		diffuseur.envoyerConsole(SOIREE, new EvenementDeTest("Joueur exclu"));

		assertThat(console.poll(PLAFOND_SECONDES, TimeUnit.SECONDS)).isNotNull().containsEntry("type", "test");
		diffuseur.diffuserSalle(SOIREE, new EvenementDeTest("témoin"));
		assertThat(donnees(salle.poll(PLAFOND_SECONDES, TimeUnit.SECONDS))).containsEntry("texte", "témoin");
		assertThat(salle).isEmpty();
	}

	/** #180 : le cookie de session ne fait de la connexion celle de l'Animateur qu'avec le jeton CSRF. */
	@Test
	void unEcranAnonymeDeDemonstrationSOuvreDansUnNavigateurConnecteEnAnimateur() throws Exception {
		var animateur = SessionAnimateurHttp.ouvrir(port, json);
		UUID demonstration = UUID.randomUUID();
		var session = connecterAvecCookie(animateur, new StompHeaders());
		var ecran = abonner(session, Canaux.salle(demonstration));

		diffuseur.diffuserSalle(demonstration, new EvenementDeTest("Vue de démonstration"));

		assertThat(ecran.poll(PLAFOND_SECONDES, TimeUnit.SECONDS)).isNotNull().containsEntry("soireeId", demonstration.toString());
	}

	@Test
	void sansJetonCsrfLeCookieNeDonnePasLaConsoleDeSonLieu() throws Exception {
		var animateur = SessionAnimateurHttp.ouvrir(port, json);
		UUID soiree = creerSoiree(animateur);
		var session = connecterAvecCookie(animateur, new StompHeaders());
		UUID abonnement = UUID.randomUUID();
		var entetes = new StompHeaders();
		entetes.setId(abonnement.toString());
		entetes.setDestination(Canaux.console(soiree));
		var console = new ArrayBlockingQueue<Map<String, Object>>(2);
		session.subscribe(entetes, collecteur(console));

		// Le broker traite les abonnements d'une même connexion dans l'ordre.
		UUID demonstration = UUID.randomUUID();
		var temoin = abonner(session, Canaux.salle(demonstration));
		assertThat(abonnements.estInstalle(abonnement, Canaux.console(soiree))).isFalse();
		diffuseur.envoyerConsole(soiree, new EvenementDeTest("Vue de la Console"));
		diffuseur.diffuserSalle(demonstration, new EvenementDeTest("témoin"));
		assertThat(temoin.poll(PLAFOND_SECONDES, TimeUnit.SECONDS)).isNotNull();
		assertThat(console).isEmpty();
	}

	@Test
	void unConnectAvecUnJetonCsrfInvalideRecoitUneTrameErrorPuisLaFermeture() throws Exception {
		var animateur = SessionAnimateurHttp.ouvrir(port, json);
		assertThat(motifDuConnectRefuse(animateur.poigneeDeMain(), "X-CSRF-TOKEN:invalide"))
				.isEqualTo("Le jeton CSRF est absent ou invalide. Resynchronise la Console d’animation.");
	}

	private StompSession connecterAvecCookie(SessionAnimateurHttp animateur, StompHeaders entetes) throws Exception {
		WebSocketStompClient client = new WebSocketStompClient(clientWebSocket());
		clients.add(client);
		client.setMessageConverter(new JacksonJsonMessageConverter());
		StompSession session = client
				.connectAsync("ws://localhost:" + port + ConfigurationTempsReel.POINT_DE_CONNEXION,
						animateur.poigneeDeMain(), entetes, new StompSessionHandlerAdapter() { })
				.get(PLAFOND_SECONDES, TimeUnit.SECONDS);
		sessions.add(session);
		return session;
	}

	private UUID creerSoiree(SessionAnimateurHttp animateur) throws Exception {
		var requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/soirees"))
				.timeout(PLAFOND).header("Content-Type", "application/json")
				.header("Cookie", animateur.cookie()).header("X-CSRF-TOKEN", animateur.csrf())
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
						Map.of("titre", "Soirée du Lieu", "format", "SOLO", "pilotage", "MANUEL")))).build();
		var reponse = http.send(requete, HttpResponse.BodyHandlers.ofString());
		assertThat(reponse.statusCode()).isEqualTo(201);
		return UUID.fromString(json.readTree(reponse.body()).path("id").asString());
	}

	private StandardWebSocketClient clientWebSocket() {
		StandardWebSocketClient client = new StandardWebSocketClient(conteneur);
		client.setTaskExecutor(executeur);
		return client;
	}

	private StompSession connecter() throws Exception {
		return connecter(null);
	}

	private StompSession connecter(Acteur acteur) throws Exception {
		String jeton = acteur == null ? null : "demo:" + acteur.type() + ":" + acteur.soireeId() + ":" + acteur.identifiant();
		return connecterAvecJeton(jeton);
	}

	private StompSession connecterAvecJeton(String jeton) throws Exception {
		WebSocketStompClient client = new WebSocketStompClient(clientWebSocket());
		clients.add(client);
		client.setMessageConverter(new JacksonJsonMessageConverter());
		StompHeaders entetes = new StompHeaders();
		if (jeton != null) {
			entetes.set("Authorization", "Bearer " + jeton);
		}
		StompSession session = client
				.connectAsync("ws://localhost:" + port + ConfigurationTempsReel.POINT_DE_CONNEXION,
						new WebSocketHttpHeaders(), entetes, new StompSessionHandlerAdapter() { })
				.get(PLAFOND_SECONDES, TimeUnit.SECONDS);
		sessions.add(session);
		return session;
	}

	/** Attend que le broker ait enregistré cet identifiant d'abonnement. */
	private BlockingQueue<Map<String, Object>> abonner(StompSession session, String destination) {
		return abonner(session, destination, UUID.randomUUID());
	}

	private BlockingQueue<Map<String, Object>> abonner(StompSession session, String destination, UUID abonnement) {
		BlockingQueue<Map<String, Object>> recus = new ArrayBlockingQueue<>(64);
		StompHeaders entetes = new StompHeaders();
		entetes.setDestination(destination);
		String identifiant = abonnement.toString();
		entetes.setId(identifiant);
		session.subscribe(entetes, collecteur(recus));
		await().atMost(PLAFOND).until(() -> abonnements.estInstalle(abonnement, destination));
		return recus;
	}

	private HttpRequest requete(UUID soireeId, String pseudo) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/test/soirees/" + soireeId + "/buzz"))
				.timeout(PLAFOND).header("Content-Type", "application/json")
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
		for (var envoi : envois) assertThat(envoi.get(PLAFOND_SECONDES, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
		List<Instant> instants = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			Map<String, Object> message = ecran.poll(PLAFOND_SECONDES, TimeUnit.SECONDS);
			assertThat(message).isNotNull();
			instants.add(Instant.parse((String) dernierBuzz(message).get("recuLe")));
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

	@Test
	void unBuzzLancePendantLaCaptureSeRaccordeSurLeFluxStompSansPerte() throws Exception {
		UUID soiree = UUID.randomUUID();
		UUID abonnement = UUID.randomUUID();
		var session = connecter();
		var messages = abonner(session, Canaux.salle(soiree), abonnement);
		CountDownLatch captureCommencee = new CountDownLatch(1);
		CountDownLatch terminerCapture = new CountDownLatch(1);
		when(repli.capturerSalle(soiree)).thenAnswer(invocation -> {
			captureCommencee.countDown();
			assertThat(terminerCapture.await(RETENUE_SECONDES, TimeUnit.SECONDS)).isTrue();
			return new VueSallePrototype(List.of());
		});
		String route = "http://localhost:" + port + "/api/test/soirees/" + soiree
				+ "/etat/salle?abonnementId=" + abonnement;
		try {
			var capture = http.sendAsync(HttpRequest.newBuilder(URI.create(route)).build(), HttpResponse.BodyHandlers.ofString());
			assertThat(captureCommencee.await(PLAFOND_SECONDES, TimeUnit.SECONDS)).isTrue();
			var buzz = http.sendAsync(requete(soiree, "Pendant la capture"), HttpResponse.BodyHandlers.ofString());
			terminerCapture.countDown();
			var reponse = capture.get(PLAFOND_SECONDES, TimeUnit.SECONDS);
			assertThat(reponse.statusCode()).isEqualTo(200);
			assertThat(json.readTree(reponse.body()).get("revision").asLong()).isZero();
			assertThat(json.readTree(reponse.body()).get("abonnementId").asString()).isEqualTo(abonnement.toString());
			assertThat(buzz.get(PLAFOND_SECONDES, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
			var evenement = messages.poll(PLAFOND_SECONDES, TimeUnit.SECONDS);
			assertThat(evenement).containsEntry("revision", 1).containsEntry("soireeId", soiree.toString());
			assertThat(dernierBuzz(evenement)).containsEntry("pseudo", "Pendant la capture");
			var suivante = http.send(HttpRequest.newBuilder(URI.create(route)).build(), HttpResponse.BodyHandlers.ofString());
			assertThat(json.readTree(suivante.body()).get("revision").asLong()).isEqualTo(1);
			session.disconnect();
			await().atMost(PLAFOND).until(() -> !abonnements.estInstalle(abonnement, Canaux.salle(soiree)));
			assertThat(http.send(HttpRequest.newBuilder(URI.create(route)).build(), HttpResponse.BodyHandlers.ofString()).statusCode())
					.isEqualTo(409);
		} finally {
			terminerCapture.countDown();
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> dernierBuzz(Map<String, Object> message) {
		return ((List<Map<String, Object>>) donnees(message).get("buzz")).getLast();
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
