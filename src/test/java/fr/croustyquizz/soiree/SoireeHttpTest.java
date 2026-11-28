package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.SessionAnimateurHttp;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.RefusDeCommande;
import fr.croustyquizz.tempsreel.ResolveurDActeur;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Les routes de Soirée sur un vrai serveur et un vrai PostgreSQL, chaque réponse validée par le contrat. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "server.servlet.context-path=/quiz")
@Import(TestcontainersConfiguration.class)
class SoireeHttpTest {

	private static final String CONTRAT = "docs/api/openapi.yaml";

	@LocalServerPort
	private int port;

	@Autowired
	private JsonMapper json;

	@Autowired
	private JdbcTemplate jdbc;

	private SessionAnimateurHttp animateur;

	@BeforeEach
	void connecterAnimateur() throws Exception {
		animateur = SessionAnimateurHttp.ouvrir(adresseServeur(), json);
	}

	@Test
	void refuseLesRequetesSansSessionEtProtegeLesMutationsParCsrf() throws Exception {
		Map<String, String> creation = Map.of("titre", "Quiz", "format", "SOLO", "pilotage", "MANUEL");
		verifierRefus("creerSoiree", envoyer(HttpMethod.POST, "/api/soirees", creation, new HttpHeaders()),
				401, "AUTHENTIFICATION_REQUISE");
		var sansCsrf = new HttpHeaders();
		sansCsrf.set(HttpHeaders.COOKIE, animateur.cookie());
		verifierRefus("creerSoiree", envoyer(HttpMethod.POST, "/api/soirees", creation, sansCsrf),
				403, "CSRF_INVALIDE");
	}

	@Test
	void isoleLesSoireesDesDeuxLieux() throws Exception {
		String id = soireeOuverte().path("id").asString();
		String route = "/api/soirees/" + id;
		var autre = SessionAnimateurHttp.ouvrir(adresseServeur(), json);
		var commande = autre.entetes();
		commande.set("Idempotency-Key", UUID.randomUUID().toString());

		for (var lecture : Map.of("", "consulterSoiree", "/qrcode", "consulterQrCodeSoiree",
				"/etat/console", "retrouverEtatConsole", "/etat/salle", "retrouverEtatSalle",
				"/etat/manette", "retrouverEtatManette").entrySet()) {
			verifierRefus(lecture.getValue(), envoyer(HttpMethod.GET, route + lecture.getKey(), null, autre.entetes()),
					404, "RESSOURCE_INTROUVABLE");
			verifierRefus(lecture.getValue(), envoyer(HttpMethod.GET, route + lecture.getKey(), null, new HttpHeaders()),
					401, "AUTHENTIFICATION_REQUISE");
		}
		for (var mutation : Map.of("/ouverture", "ouvrirSoiree", "/acces-ecran", "creerAccesEcran").entrySet()) {
			verifierRefus(mutation.getValue(), envoyer(HttpMethod.POST, route + mutation.getKey(), null, commande),
					404, "RESSOURCE_INTROUVABLE");
			verifierRefus(mutation.getValue(), envoyer(HttpMethod.POST, route + mutation.getKey(), null, new HttpHeaders()),
					401, "AUTHENTIFICATION_REQUISE");
		}
		String routePilotage = route + "/manches/" + UUID.randomUUID() + "/commandes";
		var intervention = Map.of("action", "METTRE_EN_PAUSE");
		verifierRefus("piloterManche", envoyer(HttpMethod.POST, routePilotage, intervention, commande),
				404, "RESSOURCE_INTROUVABLE");
		var sansSession = new HttpHeaders();
		sansSession.set("Idempotency-Key", UUID.randomUUID().toString());
		verifierRefus("piloterManche", envoyer(HttpMethod.POST, routePilotage, intervention, sansSession),
				401, "AUTHENTIFICATION_REQUISE");

		var capture = envoyer(HttpMethod.GET, route + "/etat/console", null, animateur.entetes());
		ContratApi.verifier(CONTRAT, "retrouverEtatConsole", capture);
		assertThat(capture.getStatusCode().value()).isEqualTo(200);
		var qrCode = envoyer(HttpMethod.GET, route + "/qrcode", null, animateur.entetes());
		ContratApi.verifier(CONTRAT, "consulterQrCodeSoiree", qrCode);
		assertThat(qrCode.getStatusCode().value()).isEqualTo(200);

		String demo = "demo:ANIMATEUR:" + id + ":" + UUID.randomUUID();
		verifierRefus("retrouverEtatConsole", envoyer(HttpMethod.GET, route + "/etat/console", null, demo),
				401, "AUTHENTIFICATION_REQUISE");
		String demoAutreSoiree = "demo:ANIMATEUR:" + UUID.randomUUID() + ":" + UUID.randomUUID();
		verifierRefus("retrouverEtatConsole", envoyer(HttpMethod.GET, route + "/etat/console", null, demoAutreSoiree),
				401, "AUTHENTIFICATION_REQUISE");
		var avecBearer = animateur.entetes();
		avecBearer.setBearerAuth(demo);
		verifierRefus("retrouverEtatConsole", envoyer(HttpMethod.GET, route + "/etat/console", null, avecBearer),
				401, "AUTHENTIFICATION_REQUISE");

		jdbc.update("update soiree set lieu_id = null where id = ?::uuid", id);
		verifierRefus("consulterSoiree", envoyer(HttpMethod.GET, route, null, animateur.entetes()),
				404, "RESSOURCE_INTROUVABLE");
	}

	/**
	 * Les routes d'Animateur du pilotage de Soirée, de la Musique d'attente, des photos et du Classement
	 * passent par le même résolveur que les routes de la Console : 404 hors du Lieu, 401 sans session.
	 */
	@Test
	void lesRoutesDePilotageDeMusiqueDePhotosEtDeClassementSuiventLeLieu() throws Exception {
		String route = "/api/soirees/" + soireeOuverte().path("id").asString();
		var autre = SessionAnimateurHttp.ouvrir(adresseServeur(), json);
		record Appel(String operation, HttpMethod methode, String chemin, Object corps) { }
		var appels = List.of(
				new Appel("piloterSoiree", HttpMethod.POST, "/commandes", Map.of("action", "LANCER")),
				new Appel("reglerMusiqueDAttente", HttpMethod.PUT, "/musique-attente", Map.of("etat", "COUPEE")),
				new Appel("reglerPhotos", HttpMethod.PUT, "/photos/reglages", Map.of("ouvertes", false)),
				new Appel("modererPhoto", HttpMethod.POST, "/photos/" + UUID.randomUUID() + "/moderation",
						Map.of("acceptee", true)),
				new Appel("consulterPhoto", HttpMethod.GET, "/photos/" + UUID.randomUUID(), null),
				new Appel("consulterClassement", HttpMethod.GET, "/classement", null));
		for (var appel : appels) {
			var horsDuLieu = autre.entetes();
			horsDuLieu.set("Idempotency-Key", UUID.randomUUID().toString());
			verifierRefus(appel.operation(), envoyer(appel.methode(), route + appel.chemin(), appel.corps(), horsDuLieu),
					404, "RESSOURCE_INTROUVABLE");
			var sansSession = new HttpHeaders();
			sansSession.set("Idempotency-Key", UUID.randomUUID().toString());
			verifierRefus(appel.operation(), envoyer(appel.methode(), route + appel.chemin(), appel.corps(), sansSession),
					401, "AUTHENTIFICATION_REQUISE");
		}
		var reglage = envoyer(HttpMethod.PUT, route + "/musique-attente", Map.of("etat", "COUPEE"));
		ContratApi.verifier(CONTRAT, "reglerMusiqueDAttente", reglage);
		assertThat(reglage.getStatusCode().value()).isEqualTo(200);
		var classement = envoyer(HttpMethod.GET, route + "/classement", null);
		ContratApi.verifier(CONTRAT, "consulterClassement", classement);
		assertThat(classement.getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void leCanalDeConsoleSuitLeLieuEtSeFermeALaDeconnexion() throws Exception {
		String id = soireeOuverte().path("id").asString();
		String route = "/api/soirees/" + id + "/etat/console";
		String canal = "/topic/soirees/" + id + "/console";
		String abonnement = UUID.randomUUID().toString();
		var autre = SessionAnimateurHttp.ouvrir(adresseServeur(), json);
		try (var client = HttpClient.newHttpClient()) {
			var proprietaire = new TramesStomp();
			var etranger = new TramesStomp();
			WebSocket socket = connecterStomp(client, animateur, proprietaire);
			WebSocket autreSocket = connecterStomp(client, autre, etranger);
			try {
				socket.sendText("SUBSCRIBE\nid:" + abonnement + "\ndestination:" + canal + "\n\n\0", true).join();
				var capture = await().atMost(Duration.ofSeconds(5)).until(
						() -> envoyer(HttpMethod.GET, route + "?abonnementId=" + abonnement, null, animateur.entetes()),
						reponse -> reponse.getStatusCode().value() == 200);
				ContratApi.verifier(CONTRAT, "retrouverEtatConsole", capture);

				String abonnementEtranger = UUID.randomUUID().toString();
				autreSocket.sendText("SUBSCRIBE\nid:" + abonnementEtranger + "\ndestination:" + canal + "\n\n\0", true).join();
				assertThat(etranger.fermeture.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
				verifierRefus("retrouverEtatConsole",
						envoyer(HttpMethod.GET, route + "?abonnementId=" + abonnementEtranger, null, animateur.entetes()),
						409, "ABONNEMENT_INACTIF");
				var deconnexion = envoyer(HttpMethod.DELETE, "/api/animateurs/session", null, animateur.entetes());
				ContratApi.verifier(CONTRAT, "deconnecterAnimateur", deconnexion);
				assertThat(deconnexion.getStatusCode().value()).isEqualTo(204);
				assertThat(proprietaire.fermeture.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
				verifierRefus("retrouverEtatConsole", envoyer(HttpMethod.GET, route, null, animateur.entetes()),
						401, "AUTHENTIFICATION_REQUISE");
			} finally {
				socket.abort();
				autreSocket.abort();
			}
		}
	}

	private WebSocket connecterStomp(HttpClient client, SessionAnimateurHttp session, TramesStomp reception)
			throws Exception {
		WebSocket socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
				.subprotocols("v12.stomp").header("Cookie", session.cookie())
				.buildAsync(URI.create(adresseServeur().replaceFirst("^http", "ws") + "/ws"), reception).get(5, TimeUnit.SECONDS);
		socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nX-CSRF-TOKEN:" + session.csrf() + "\n\n\0", true).join();
		assertThat(reception.trames.poll(5, TimeUnit.SECONDS)).startsWith("CONNECTED");
		return socket;
	}

	private static final class TramesStomp implements WebSocket.Listener {
		private final LinkedBlockingQueue<String> trames = new LinkedBlockingQueue<>();
		private final CompletableFuture<Integer> fermeture = new CompletableFuture<>();
		private final StringBuilder trame = new StringBuilder();

		@Override
		public CompletionStage<?> onText(WebSocket socket, CharSequence fragment, boolean fin) {
			trame.append(fragment);
			if (fin) {
				trames.add(trame.toString());
				trame.setLength(0);
			}
			return WebSocket.Listener.super.onText(socket, fragment, fin);
		}

		@Override
		public CompletionStage<?> onClose(WebSocket socket, int statut, String raison) {
			fermeture.complete(statut);
			return WebSocket.Listener.super.onClose(socket, statut, raison);
		}
		@Override
		public void onError(WebSocket socket, Throwable erreur) {
			fermeture.completeExceptionally(erreur);
		}

	}

	@Autowired
	private ResolveurDActeur resolveur;
	@MockitoSpyBean
	private TirageDeCode tirage;

	@AfterEach
	void effacerLaBase() {
		jdbc.update("delete from acces_ecran");
		jdbc.update("delete from joueur");
		jdbc.update("delete from soiree");
	}

	@Test
	void creerConsulterOuvrirPuisRejoindreSuiventLeContrat() throws Exception {
		var creation = envoyer(HttpMethod.POST, "/api/soirees",
				Map.of("titre", "Quiz du jeudi", "format", "EQUIPE", "pilotage", "AUTOMATIQUE"));
		ContratApi.verifier(CONTRAT, "creerSoiree", creation);
		assertThat(creation.getStatusCode().value()).isEqualTo(201);
		assertThat(creation.getHeaders().getCacheControl()).isEqualTo("no-store");
		JsonNode soiree = json.readTree(creation.getBody());
		String id = soiree.path("id").asString();
		assertThat(creation.getHeaders().getLocation()).hasToString("/api/soirees/" + id);
		assertThat(soiree.path("etat").asString()).isEqualTo("PREPARATION");
		assertThat(soiree.path("format").asString()).isEqualTo("EQUIPE");
		assertThat(soiree.path("pilotage").asString()).isEqualTo("AUTOMATIQUE");
		assertThat(soiree.has("codeAcces")).isTrue();
		assertThat(soiree.path("codeAcces").isNull()).isTrue();

		var consultation = envoyer(HttpMethod.GET, "/api/soirees/" + id, null);
		ContratApi.verifier(CONTRAT, "consulterSoiree", consultation);
		assertThat(consultation.getBody()).isEqualTo(creation.getBody());

		var ouverture = envoyer(HttpMethod.POST, "/api/soirees/" + id + "/ouverture", null);
		ContratApi.verifier(CONTRAT, "ouvrirSoiree", ouverture);
		String code = json.readTree(ouverture.getBody()).path("codeAcces").asString();
		assertThat(json.readTree(ouverture.getBody()).path("etat").asString()).isEqualTo("OUVERTE");
		var repetition = envoyer(HttpMethod.POST, "/api/soirees/" + id + "/ouverture", null);
		ContratApi.verifier(CONTRAT, "ouvrirSoiree", repetition);
		assertThat(repetition.getBody()).isEqualTo(ouverture.getBody());

		var arrivee = envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", code, "pseudo", " Léa "));
		ContratApi.verifier(CONTRAT, "rejoindreSoiree", arrivee);
		assertThat(arrivee.getStatusCode().value()).isEqualTo(201);
		assertThat(arrivee.getHeaders().getCacheControl()).isEqualTo("no-store");
		JsonNode acces = json.readTree(arrivee.getBody());
		assertThat(acces.path("soireeId").asString()).isEqualTo(id);
		assertThat(acces.at("/joueur/pseudo").asString()).isEqualTo("Léa");
		assertThat(arrivee.getHeaders().getLocation()).hasToString("/api/soirees/" + id + "/etat/manette");
		String jeton = acces.path("jetonReconnexion").asString();
		byte[] empreinte = jdbc.queryForObject("select empreinte_jeton from joueur where id = ?::uuid", byte[].class,
				acces.at("/joueur/id").asString());
		assertThat(empreinte).isEqualTo(new JetonDeReconnexion(jeton).empreinte());
	}

	@Test
	void lesRefusDeLaConsoleSuiventLeContrat() throws Exception {
		var sansTitre = envoyer(HttpMethod.POST, "/api/soirees",
				Map.of("titre", "   ", "format", "SOLO", "pilotage", "MANUEL"));
		verifierRefus("creerSoiree", sansTitre, 422, "DONNEES_INVALIDES");
		// Un champ requis absent viole le schéma : 400, comme un Format absent.
		verifierRefus("creerSoiree", envoyer(HttpMethod.POST, "/api/soirees",
				Map.of("format", "SOLO", "pilotage", "MANUEL")), 400, "REQUETE_INVALIDE");

		Map<String, Object> sansFormat = new HashMap<>(Map.of("titre", "Quiz", "pilotage", "MANUEL"));
		verifierRefus("creerSoiree", envoyer(HttpMethod.POST, "/api/soirees", sansFormat), 400, "REQUETE_INVALIDE");
		verifierRefus("creerSoiree", envoyer(HttpMethod.POST, "/api/soirees",
				Map.of("titre", "Quiz", "format", "DUO", "pilotage", "MANUEL")), 400, "REQUETE_INVALIDE");

		String inconnue = "/api/soirees/" + UUID.randomUUID();
		verifierRefus("consulterSoiree", envoyer(HttpMethod.GET, inconnue, null), 404, "RESSOURCE_INTROUVABLE");
		verifierRefus("ouvrirSoiree", envoyer(HttpMethod.POST, inconnue + "/ouverture", null), 404,
				"RESSOURCE_INTROUVABLE");
		verifierRefus("consulterSoiree", envoyer(HttpMethod.GET, "/api/soirees/pas-un-uuid", null), 400,
				"REQUETE_INVALIDE");

		String lancee = soireeOuverte().path("id").asString();
		jdbc.update("update soiree set etat = 'EN_COURS' where id = ?::uuid", lancee);
		verifierRefus("ouvrirSoiree", envoyer(HttpMethod.POST, "/api/soirees/" + lancee + "/ouverture", null), 409,
				"ETAT_INCOMPATIBLE");
	}

	@Test
	void lesRefusDeLArriveeSuiventLeContrat() throws Exception {
		JsonNode ouverte = soireeOuverte();
		String code = ouverte.path("codeAcces").asString();

		verifierRefus("rejoindreSoiree", envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", "ZZZZZZ".equals(code) ? "YYYYYY" : "ZZZZZZ", "pseudo", "Léa")), 404,
				"CODE_ACCES_INVALIDE");
		verifierRefus("rejoindreSoiree", envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", code, "pseudo", "  ")), 422, "DONNEES_INVALIDES");
		verifierRefus("rejoindreSoiree", envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", code, "pseudo", "x".repeat(Joueur.LONGUEUR_MAX_PSEUDO + 1))), 422,
				"DONNEES_INVALIDES");

		jdbc.update("update soiree set etat = 'TERMINEE' where id = ?::uuid", ouverte.path("id").asString());
		verifierRefus("rejoindreSoiree", envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", code, "pseudo", "Léa")), 404, "CODE_ACCES_INVALIDE");
		assertThat(jdbc.queryForObject("select count(*) from joueur", Integer.class)).isZero();
	}

	private JsonNode soireeOuverte() throws Exception {
		var creation = envoyer(HttpMethod.POST, "/api/soirees",
				Map.of("titre", "Quiz du jeudi", "format", "SOLO", "pilotage", "MANUEL"));
		ContratApi.verifier(CONTRAT, "creerSoiree", creation);
		String id = json.readTree(creation.getBody()).path("id").asString();
		var ouverture = envoyer(HttpMethod.POST, "/api/soirees/" + id + "/ouverture", null);
		ContratApi.verifier(CONTRAT, "ouvrirSoiree", ouverture);
		return json.readTree(ouverture.getBody());
	}

	private void verifierRefus(String operationId, ResponseEntity<String> reponse, int statut, String code)
			throws Exception {
		ContratApi.verifier(CONTRAT, operationId, reponse);
		assertThat(reponse.getStatusCode().value()).isEqualTo(statut);
		assertThat(json.readTree(reponse.getBody()).path("code").asString()).isEqualTo(code);
		assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("no-store");
	}

	@Test
	void lEcranDeSalleVoitLesJoueursEtChacunNeVoitQueSaPortee() throws Exception {
		JsonNode soiree = soireeOuverte();
		String id = soiree.path("id").asString();
		String code = soiree.path("codeAcces").asString();
		JsonNode lea = json.readTree(envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", code, "pseudo", "Léa")).getBody());
		envoyer(HttpMethod.POST, "/api/soirees/rejoindre", Map.of("codeAcces", code, "pseudo", "Tom"));

		var ouvertureEcran = envoyer(HttpMethod.POST, "/api/soirees/" + id + "/acces-ecran", null);
		ContratApi.verifier(CONTRAT, "creerAccesEcran", ouvertureEcran);
		assertThat(ouvertureEcran.getHeaders().getCacheControl()).isEqualTo("no-store");
		String cle = json.readTree(ouvertureEcran.getBody()).path("cleEcran").asString();

		var salle = envoyer(HttpMethod.GET, "/api/soirees/" + id + "/etat/salle", null, cle);
		ContratApi.verifier(CONTRAT, "retrouverEtatSalle", salle);
		JsonNode vue = json.readTree(salle.getBody()).path("vue");
		assertThat(vue.at("/soiree/codeAcces").asString()).isEqualTo(code);
		assertThat(vue.path("joueurs").findValuesAsString("pseudo")).containsExactly("Léa", "Tom");
		assertThat(vue.path("mancheActive").isNull()).isTrue();

		var console = envoyer(HttpMethod.GET, "/api/soirees/" + id + "/etat/console", null, cle);
		ContratApi.verifier(CONTRAT, "retrouverEtatConsole", console);
		assertThat(console.getStatusCode().value()).isEqualTo(403);

		String jeton = lea.path("jetonReconnexion").asString();
		var manette = envoyer(HttpMethod.GET, "/api/soirees/" + id + "/etat/manette", null, jeton);
		ContratApi.verifier(CONTRAT, "retrouverEtatManette", manette);
		assertThat(json.readTree(manette.getBody()).at("/vue/joueurId").asString())
				.isEqualTo(lea.at("/joueur/id").asString());

		String autre = soireeOuverte().path("id").asString();
		var horsPortee = envoyer(HttpMethod.GET, "/api/soirees/" + autre + "/etat/salle", null, cle);
		ContratApi.verifier(CONTRAT, "retrouverEtatSalle", horsPortee);
		assertThat(horsPortee.getStatusCode().value()).isEqualTo(404);

		var inconnue = envoyer(HttpMethod.GET, "/api/soirees/" + id + "/etat/salle", null, "a".repeat(43));
		ContratApi.verifier(CONTRAT, "retrouverEtatSalle", inconnue);
		assertThat(inconnue.getStatusCode().value()).isEqualTo(401);
	}

	@Test
	void leQrCodeMeneALaManetteAvecLeCodeDUneSoireeOuverte() throws Exception {
		String preparee = json.readTree(envoyer(HttpMethod.POST, "/api/soirees",
				Map.of("titre", "Quiz", "format", "SOLO", "pilotage", "MANUEL")).getBody()).path("id").asString();
		String cle = cleEcran(preparee);
		var tropTot = envoyer(HttpMethod.GET, "/api/soirees/" + preparee + "/qrcode", null, cle);
		verifierRefus("consulterQrCodeSoiree", tropTot, 409, "SOIREE_NON_OUVERTE");

		JsonNode ouverte = json.readTree(envoyer(HttpMethod.POST, "/api/soirees/" + preparee + "/ouverture", null)
				.getBody());
		var qrCode = envoyer(HttpMethod.GET, "/api/soirees/" + preparee + "/qrcode", null, cle);

		ContratApi.verifier(CONTRAT, "consulterQrCodeSoiree", qrCode);
		assertThat(qrCode.getStatusCode().value()).isEqualTo(200);
		assertThat(qrCode.getHeaders().getContentType()).hasToString("image/svg+xml");
		assertThat(qrCode.getHeaders().getCacheControl()).isEqualTo("no-store");
		assertThat(QrCodeSvgTest.relire(qrCode.getBody()))
				.isEqualTo(adresseServeur() + "/rejoindre.html#code=" + ouverte.path("codeAcces").asString());
	}

	@Test
	void derriereUnRelaisHttpsLeQrCodeMeneALAdressePublique() throws Exception {
		JsonNode ouverte = soireeOuverte();
		String id = ouverte.path("id").asString();

		var qrCode = RestClient.create(adresseServeur()).get().uri("/api/soirees/" + id + "/qrcode")
				.header("Authorization", "Bearer " + cleEcran(id))
				.header("X-Forwarded-Proto", "https")
				.header("X-Forwarded-Host", "croustyquizz.example")
				.retrieve().toEntity(String.class);

		ContratApi.verifier(CONTRAT, "consulterQrCodeSoiree", qrCode);
		String adresse = "https://croustyquizz.example/quiz/rejoindre.html";
		assertThat(qrCode.getHeaders().getFirst("Adresse-Manette")).isEqualTo(adresse);
		assertThat(QrCodeSvgTest.relire(qrCode.getBody()))
				.isEqualTo(adresse + "#code=" + ouverte.path("codeAcces").asString());
	}

	@Test
	void leQrCodeRefuseLesSecretsHorsDeLeurPortee() throws Exception {
		JsonNode ouverte = soireeOuverte();
		String id = ouverte.path("id").asString();
		String route = "/api/soirees/" + id + "/qrcode";

		verifierRefus("consulterQrCodeSoiree", envoyer(HttpMethod.GET, route, null), 401, "AUTHENTIFICATION_REQUISE");
		String cleDUneAutre = cleEcran(soireeOuverte().path("id").asString());
		verifierRefus("consulterQrCodeSoiree", envoyer(HttpMethod.GET, route, null, cleDUneAutre), 404,
				"RESSOURCE_INTROUVABLE");
		verifierRefus("consulterQrCodeSoiree", envoyer(HttpMethod.GET, "/api/soirees/" + UUID.randomUUID() + "/qrcode",
				null, cleDUneAutre), 404, "RESSOURCE_INTROUVABLE");
		String jeton = json.readTree(envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", ouverte.path("codeAcces").asString(), "pseudo", "Léa")).getBody())
				.path("jetonReconnexion").asString();
		verifierRefus("consulterQrCodeSoiree", envoyer(HttpMethod.GET, route, null, jeton), 403, "ACCES_REFUSE");
	}

	private String cleEcran(String soireeId) {
		return json.readTree(envoyer(HttpMethod.POST, "/api/soirees/" + soireeId + "/acces-ecran", null).getBody())
				.path("cleEcran").asString();
	}

	@Test
	void unPseudoInjurieuxOuDejaPrisEstRefuseSelonLeContrat() throws Exception {
		String code = soireeOuverte().path("codeAcces").asString();
		envoyer(HttpMethod.POST, "/api/soirees/rejoindre", Map.of("codeAcces", code, "pseudo", "Léa"));

		verifierRefus("rejoindreSoiree", envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", code, "pseudo", "LÉA")), 409, "PSEUDO_DEJA_PRIS");
		verifierRefus("rejoindreSoiree", envoyer(HttpMethod.POST, "/api/soirees/rejoindre",
				Map.of("codeAcces", code, "pseudo", "s@l0pe")), 422, "PSEUDO_REFUSE");
	}

	@Test
	void lesTroisEcransDuParcoursSontServis() {
		for (String page : new String[] { "/preparer-soiree.html", "/ecran.html", "/rejoindre.html" }) {
			var reponse = envoyer(HttpMethod.GET, page, null);
			assertThat(reponse.getStatusCode().value()).as(page).isEqualTo(200);
			assertThat(reponse.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).as(page).isTrue();
		}
		assertThat(envoyer(HttpMethod.GET, "/js/api.js", null).getBody()).contains("export async function appeler");
	}

	@Test
	void lEcranDeSalleDuParcoursEstLaCoquilleCommune() {
		// La préparation ouvre ecran.html avec la clé d'Écran ; l'ancienne page dédiée n'est plus servie (#106).
		// Son script est dans un module depuis #282.
		assertThat(envoyer(HttpMethod.GET, "/preparer-soiree.html", null).getBody())
				.contains("src=\"/js/preparation-soiree.mjs\"");
		assertThat(envoyer(HttpMethod.GET, "/js/preparation-soiree.mjs", null).getBody()).contains("/ecran.html?");
		assertThat(envoyer(HttpMethod.GET, "/ecran-de-salle.html", null).getStatusCode().value()).isEqualTo(404);
	}

	@Test
	void uneSoireeAbsenteDeLaBaseResteCelleDeLaDemonstration() throws Exception {
		UUID soiree = UUID.randomUUID();
		String jetonDemo = "demo:ECRAN:" + soiree + ":" + UUID.randomUUID();

		var salle = envoyer(HttpMethod.GET, "/api/soirees/" + soiree + "/etat/salle", null, jetonDemo);

		ContratApi.verifier(CONTRAT, "retrouverEtatSalle", salle);
		assertThat(salle.getStatusCode().value()).isEqualTo(200);
		assertThat(json.readTree(salle.getBody()).at("/vue/soiree/titre").asString())
				.isEqualTo("Soirée de démonstration");
	}

	@Test
	void uneIdentiteDeDemonstrationDeJoueurOuDEcranNeVisePasUneSoireeEnregistree() throws Exception {
		String soiree = soireeOuverte().path("id").asString();
		String joueur = "demo:JOUEUR:" + soiree + ":" + UUID.randomUUID();
		String ecran = "demo:ECRAN:" + soiree + ":" + UUID.randomUUID();

		verifierRefus("retrouverEtatManette",
				envoyer(HttpMethod.GET, "/api/soirees/" + soiree + "/etat/manette", null, joueur), 401, "AUTHENTIFICATION_REQUISE");
		var salle = envoyer(HttpMethod.GET, "/api/soirees/" + soiree + "/etat/salle", null, ecran);
		verifierRefus("retrouverEtatSalle", salle, 401, "AUTHENTIFICATION_REQUISE");
		assertThat(json.readTree(salle.getBody()).path("detail").asString()).contains("clé créée depuis la Console");
		for (String jeton : List.of(joueur, ecran)) {
			verifierRefusAuConnect(jeton);
		}

		// L'Animateur de démonstration non plus (#5) : la Console d'une Soirée enregistrée passe par la session.
		String animateurDemo = "demo:ANIMATEUR:" + soiree + ":" + UUID.randomUUID();
		verifierRefus("retrouverEtatConsole", envoyer(HttpMethod.GET, "/api/soirees/" + soiree + "/etat/console", null,
				animateurDemo), 401, "AUTHENTIFICATION_REQUISE");
		verifierRefusAuConnect(animateurDemo);
		var console = envoyer(HttpMethod.GET, "/api/soirees/" + soiree + "/etat/console", null);
		ContratApi.verifier(CONTRAT, "retrouverEtatConsole", console);
		assertThat(console.getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void uneSoireeFictiveNePretePasSonIdentiteDeDemonstrationAUnVraiJoueur() throws Exception {
		String code = soireeOuverte().path("codeAcces").asString();
		var arrivee = envoyer(HttpMethod.POST, "/api/soirees/rejoindre", Map.of("codeAcces", code, "pseudo", "Léa"));
		ContratApi.verifier(CONTRAT, "rejoindreSoiree", arrivee);
		assertThat(arrivee.getStatusCode().value()).isEqualTo(201);
		// Un UUID vide ferait refuser le jeton pour une autre raison que le vrai Joueur.
		String lea = UUID.fromString(json.readTree(arrivee.getBody()).at("/joueur/id").asString()).toString();
		UUID fictive = UUID.randomUUID();
		String jeton = "demo:JOUEUR:" + fictive + ":" + lea;

		verifierRefus("retrouverEtatManette",
				envoyer(HttpMethod.GET, "/api/soirees/" + fictive + "/etat/manette", null, jeton), 401,
				"AUTHENTIFICATION_REQUISE");
		verifierRefusAuConnect(jeton);
	}

	@Test
	void unJoueurExcluRecoitUnMotifNeutreAuConnect() throws Exception {
		String code = soireeOuverte().path("codeAcces").asString();
		var reponse = envoyer(HttpMethod.POST, "/api/soirees/rejoindre", Map.of("codeAcces", code, "pseudo", "Léa"));
		ContratApi.verifier(CONTRAT, "rejoindreSoiree", reponse);
		assertThat(reponse.getStatusCode().value()).isEqualTo(201);
		var arrivee = json.readTree(reponse.getBody());
		UUID joueur = UUID.fromString(arrivee.at("/joueur/id").asString());
		var connexion = StompHeaderAccessor.create(StompCommand.CONNECT);
		connexion.setNativeHeader("Authorization", "Bearer " + arrivee.path("jetonReconnexion").asString());
		assertThat(resolveur.resoudre(connexion).identifiant()).isEqualTo(joueur);

		jdbc.update("update joueur set exclu = true where id = ?", joueur);

		assertThatThrownBy(() -> resolveur.resoudre(connexion))
				.isInstanceOfSatisfying(RefusDeCommande.class, refus -> {
					assertThat(refus.refus().statutHttp()).isEqualTo(401);
					assertThat(refus.refus().code()).isEqualTo("AUTHENTIFICATION_REQUISE");
					assertThat(refus.refus().detail()).isEqualTo("Tu ne fais plus partie de cette Soirée.");
				});
	}

	/** Le CONNECT STOMP passe par le même résolveur que HTTP ; son refus devient une trame ERROR. */
	private void verifierRefusAuConnect(String jeton) {
		var connexion = StompHeaderAccessor.create(StompCommand.CONNECT);
		connexion.setNativeHeader("Authorization", "Bearer " + jeton);
		assertThatThrownBy(() -> resolveur.resoudre(connexion)).as(jeton)
				.isInstanceOfSatisfying(RefusDeCommande.class, refus -> {
					assertThat(refus.refus().statutHttp()).isEqualTo(401);
					assertThat(refus.refus().code()).isEqualTo("AUTHENTIFICATION_REQUISE");
				});
	}

	@Test
	void sansCodeLibreApresTousLesEssaisLOuvertureRepond503EtLaSoireeResteEnPreparation() throws Exception {
		String pris = soireeOuverte().path("codeAcces").asString();
		String id = json.readTree(envoyer(HttpMethod.POST, "/api/soirees",
				Map.of("titre", "Quiz", "format", "SOLO", "pilotage", "MANUEL")).getBody()).path("id").asString();
		doReturn(pris).when(tirage).tirer();

		verifierRefus("ouvrirSoiree", envoyer(HttpMethod.POST, "/api/soirees/" + id + "/ouverture", null), 503,
				"SERVICE_INDISPONIBLE");
		assertThat(json.readTree(envoyer(HttpMethod.GET, "/api/soirees/" + id, null).getBody()).path("etat").asString())
				.isEqualTo("PREPARATION");
	}

	private ResponseEntity<String> envoyer(HttpMethod methode, String route, Object corps) {
		boolean routeDeConsole = route.startsWith("/api/soirees") && !route.equals("/api/soirees/rejoindre")
				&& !route.endsWith("/qrcode");
		return envoyer(methode, route, corps, routeDeConsole ? animateur.entetes() : new HttpHeaders());
	}

	private ResponseEntity<String> envoyer(HttpMethod methode, String route, Object corps, String secret) {
		var entetes = new HttpHeaders();
		entetes.setBearerAuth(secret);
		return envoyer(methode, route, corps, entetes);
	}

	private ResponseEntity<String> envoyer(HttpMethod methode, String route, Object corps, HttpHeaders entetes) {
		var requete = RestClient.create(adresseServeur()).method(methode).uri(route)
				.headers(envoi -> envoi.addAll(entetes));
		if (methode == HttpMethod.POST && route.endsWith("/ouverture")) {
			requete.header("Idempotency-Key", UUID.randomUUID().toString());
		}
		if (corps != null) {
			requete.contentType(MediaType.APPLICATION_JSON).body(corps);
		}
		return requete.retrieve().onStatus(statut -> statut.isError(), (demande, reponse) -> { })
				.toEntity(String.class);
	}

	private String adresseServeur() {
		return "http://localhost:" + port + "/quiz";
	}

}
