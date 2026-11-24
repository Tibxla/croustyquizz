package fr.croustyquizz.manche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.tempsreel.Canaux;
import fr.croustyquizz.tempsreel.RegistreAbonnements;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, SocleMancheJpaTest.Configuration.class})
class DeroulementSoireeTempsReelTest {
	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final String PILOTER_SOIREE = "piloterSoiree";
	private static final String PILOTER_MANCHE = "piloterManche";
	private static final String COMMANDES = "/commandes";
	@LocalServerPort private int port;
	@Autowired private JsonMapper json;
	@Autowired private Horloge horloge;
	@Autowired private RegistreAbonnements abonnements;
	private final WsWebSocketContainer conteneur = new WsWebSocketContainer();
	private final SimpleAsyncTaskExecutor executeur = new SimpleAsyncTaskExecutor("soiree-stomp-");
	private final List<StompSession> sessions = new ArrayList<>();
	private final List<WebSocketStompClient> clients = new ArrayList<>();

	@AfterEach
	void fermerLesConnexions() {
		sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
		clients.forEach(WebSocketStompClient::stop);
		executeur.close();
		conteneur.destroy();
	}

	@Test
	void deuxManchesDansLOrdreAvecPauseRepriseRecaptureEtFinSurTousLesEcrans() throws Exception {
		var creation = envoyer("creerSoiree", HttpMethod.POST, "/api/soirees", null,
				Map.of("titre", "Soirée de deux Manches", "format", "SOLO", "pilotage", "MANUEL"), null, 201);
		UUID soiree = UUID.fromString(creation.path("id").asString());
		String racine = "/api/soirees/" + soiree;
		String animateur = "demo:ANIMATEUR:" + soiree + ":" + UUID.randomUUID();
		String seconde = ajouterManche(racine, animateur);
		String premiere = ajouterManche(racine, animateur);
		envoyer("ordonnerManches", HttpMethod.PUT, racine + "/ordre-des-manches", animateur,
				Map.of("mancheIds", List.of(premiere, seconde)), null, 200);
		var ouverte = envoyer("ouvrirSoiree", HttpMethod.POST, racine + "/ouverture", animateur, null, UUID.randomUUID(), 200);
		var joueur = envoyer("rejoindreSoiree", HttpMethod.POST, "/api/soirees/rejoindre", null,
				Map.of("codeAcces", ouverte.path("codeAcces").asString(), "pseudo", "Camille"), null, 201);
		var accesEcran = envoyer("creerAccesEcran", HttpMethod.POST, racine + "/acces-ecran", animateur, null, null, 200);
		String cleEcran = accesEcran.path("cleEcran").asString();

		var console = abonner(animateur, Canaux.console(soiree), "console", "retrouverEtatConsole");
		var salle = abonner(cleEcran, Canaux.salle(soiree), "salle", "retrouverEtatSalle");
		var manette = abonner(joueur.path("jetonReconnexion").asString(),
				Canaux.joueur(UUID.fromString(joueur.at("/joueur/id").asString())), "manette", "retrouverEtatManette");
		var flux = List.of(console, salle, manette);
		for (var ecran : flux) {
			assertThat(capturer(racine, ecran).at("/vue/soiree/etat").asString()).isEqualTo("OUVERTE");
		}

		String pilotageSoiree = racine + COMMANDES;
		String pilotagePremiere = racine + "/manches/" + premiere + COMMANDES;
		String pilotageSeconde = racine + "/manches/" + seconde + COMMANDES;
		commander(PILOTER_SOIREE, pilotageSoiree, animateur, "LANCER", UUID.randomUUID(), 200);
		verifierDemarrage(flux, premiere, 0);

		var horlogeFactice = (HorlogeFactice) horloge;
		horlogeFactice.avancer(Duration.ofSeconds(7));
		var pause = commander(PILOTER_MANCHE, pilotagePremiere, animateur, "METTRE_EN_PAUSE", UUID.randomUUID(), 200);
		assertThat(Instant.parse(pause.path("recueLe").asString())).isEqualTo(horloge.maintenant());
		for (var ecran : flux) {
			var vue = lire(ecran, "manche-en-pause").path("donnees");
			assertThat(vue.at("/quiz/tempsRestantMs").asLong()).isEqualTo(13_000);
		}
		horlogeFactice.avancer(Duration.ofSeconds(25));
		var reprise = commander(PILOTER_MANCHE, pilotagePremiere, animateur, "REPRENDRE", UUID.randomUUID(), 200);
		assertThat(Instant.parse(reprise.path("recueLe").asString())).isEqualTo(horloge.maintenant());
		for (var ecran : flux) {
			var vue = lire(ecran, "manche-reprise").path("donnees");
			assertThat(vue.at("/quiz/question/numero").asInt()).isOne();
			assertThat(Instant.parse(vue.at("/quiz/echeanceLe").asString())).isEqualTo(horloge.maintenant().plusSeconds(13));
		}

		commander(PILOTER_MANCHE, pilotagePremiere, animateur, "PASSER_ETAPE", UUID.randomUUID(), 200);
		lire(console, "question-ouverte");
		lire(manette, "question-ouverte");
		var avantRechargement = lire(salle, "question-ouverte");
		assertThat(avantRechargement.at("/donnees/quiz/question/numero").asInt()).isEqualTo(2);
		salle.session().disconnect();
		var salleRechargee = abonner(cleEcran, Canaux.salle(soiree), "salle", "retrouverEtatSalle");
		var capture = capturer(racine, salleRechargee);
		assertThat(capture.path("revision")).isEqualTo(avantRechargement.path("revision"));
		assertThat(capture.path("vue")).isEqualTo(avantRechargement.path("donnees"));
		var fluxApresRechargement = List.of(console, salleRechargee, manette);

		horlogeFactice.avancer(Duration.ofSeconds(40));
		for (var ecran : fluxApresRechargement) {
			lire(ecran, "question-ouverte");
			var finManche = lire(ecran, "manche-terminee").path("donnees");
			assertThat(finManche.at("/soiree/etat").asString()).isEqualTo("EN_COURS");
			assertThat(finManche.path("mancheActive").isNull()).isTrue();
		}
		UUID cleSuivante = UUID.randomUUID();
		var suivante = commander(PILOTER_SOIREE, pilotageSoiree, animateur, "MANCHE_SUIVANTE", cleSuivante, 200);
		verifierDemarrage(fluxApresRechargement, seconde, 1);

		UUID cleFin = UUID.randomUUID();
		var fin = commander(PILOTER_SOIREE, pilotageSoiree, animateur, "TERMINER", cleFin, 200);
		for (var ecran : fluxApresRechargement) {
			lire(ecran, "manche-terminee");
			var evenement = lire(ecran, "soiree-terminee");
			assertThat(evenement.at("/donnees/soiree/etat").asString()).isEqualTo("TERMINEE");
			assertThat(evenement.at("/donnees/mancheActive").isNull()).isTrue();
			assertThat(evenement.at("/donnees/modeEnCours").isNull()).isTrue();
			assertThat(capturer(racine, ecran).path("vue")).isEqualTo(evenement.path("donnees"));
		}
		var refusee = commander(PILOTER_MANCHE, pilotageSeconde, animateur, "METTRE_EN_PAUSE", UUID.randomUUID(), 409);
		assertThat(refusee.path("code").asString()).isEqualTo("ETAT_INCOMPATIBLE");
		assertThat(commander(PILOTER_SOIREE, pilotageSoiree, animateur, "MANCHE_SUIVANTE", cleSuivante, 200)).isEqualTo(suivante);
		assertThat(commander(PILOTER_SOIREE, pilotageSoiree, animateur, "TERMINER", cleFin, 200)).isEqualTo(fin);
	}

	private void verifierDemarrage(List<Abonnement> flux, String manche, int ordre) throws InterruptedException {
		for (var ecran : flux) {
			var vue = lire(ecran, "manche-demarree").path("donnees");
			assertThat(vue.at("/soiree/etat").asString()).isEqualTo("EN_COURS");
			assertThat(vue.at("/mancheActive/id").asString()).isEqualTo(manche);
			assertThat(vue.at("/mancheActive/ordre").asInt()).isEqualTo(ordre);
			assertThat(vue.path("modeEnCours").asString()).isEqualTo("QUIZ");
			assertThat(vue.at("/quiz/question/numero").asInt()).isOne();
		}
	}

	private String ajouterManche(String racine, String animateur) throws Exception {
		return envoyer("ajouterManche", HttpMethod.POST, racine + "/manches", animateur,
				Map.of("mode", "QUIZ", "reglages", Map.of("nombreEtapes", 3, "tempsParEtapeSecondes", 20)), null, 201)
				.path("id").asString();
	}

	private JsonNode commander(String operation, String route, String animateur, String action, UUID cle, int statut) throws Exception {
		return envoyer(operation, HttpMethod.POST, route, animateur, Map.of("action", action), cle, statut);
	}

	private JsonNode envoyer(String operation, HttpMethod methode, String route, String jeton, Object corps, UUID cle, int statut)
			throws Exception {
		var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route);
		if (jeton != null) {
			requete.header("Authorization", "Bearer " + jeton);
		}
		if (cle != null) {
			requete.header("Idempotency-Key", cle.toString());
		}
		if (corps != null) {
			requete.contentType(MediaType.APPLICATION_JSON).body(corps);
		}
		var reponse = requete.retrieve().onStatus(code -> code.isError(), (demande, resultat) -> { }).toEntity(String.class);
		ContratApi.verifier(CONTRAT, operation, reponse);
		assertThat(reponse.getStatusCode().value()).as(operation).isEqualTo(statut);
		return json.readTree(reponse.getBody());
	}

	private Abonnement abonner(String jeton, String canal, String ecran, String operation) throws Exception {
		var websocket = new StandardWebSocketClient(conteneur);
		websocket.setTaskExecutor(executeur);
		var client = new WebSocketStompClient(websocket);
		clients.add(client);
		client.setMessageConverter(new JacksonJsonMessageConverter());
		var connexion = new StompHeaders();
		connexion.set("Authorization", "Bearer " + jeton);
		var session = client.connectAsync("ws://localhost:" + port + "/ws",
				new WebSocketHttpHeaders(), connexion, new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
		sessions.add(session);
		UUID id = UUID.randomUUID();
		var entetes = new StompHeaders();
		entetes.setId(id.toString());
		entetes.setDestination(canal);
		BlockingQueue<JsonNode> messages = new ArrayBlockingQueue<>(16);
		session.subscribe(entetes, new StompFrameHandler() {
			@Override public Type getPayloadType(StompHeaders entetes) { return Map.class; }
			@Override public void handleFrame(StompHeaders entetes, Object corps) { messages.add(json.valueToTree(corps)); }
		});
		await().atMost(Duration.ofSeconds(3)).until(() -> abonnements.estInstalle(id, canal));
		return new Abonnement(jeton, ecran, operation, id, session, messages);
	}

	private JsonNode capturer(String racine, Abonnement abonne) throws Exception {
		return envoyer(abonne.operation(), HttpMethod.GET, racine + "/etat/" + abonne.ecran() + "?abonnementId=" + abonne.id(),
				abonne.jeton(), null, null, 200);
	}

	private static JsonNode lire(Abonnement abonne, String type) throws InterruptedException {
		var evenement = abonne.messages().poll(3, TimeUnit.SECONDS);
		assertThat(evenement).as("%s sur %s", type, abonne.ecran()).isNotNull();
		assertThat(evenement.path("type").asString()).isEqualTo(type);
		return evenement;
	}

	private record Abonnement(String jeton, String ecran, String operation, UUID id, StompSession session, BlockingQueue<JsonNode> messages) { }
}
