package fr.croustyquizz;

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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import fr.croustyquizz.tempsreel.ConnexionsDesManettes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Critère de sortie du jalon 1 (#8), en production : aucune identité de démonstration n'y est acceptée.
 * Un Animateur inscrit son Lieu, se connecte par session et jeton CSRF, crée et ouvre une Soirée ; un Joueur
 * la rejoint par son code et buzze ; l'Écran de salle et la Console voient en direct le Buzz, l'arrivée du
 * Joueur, puis sa Manette se déconnecter et revenir.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("prod")
class SoireeDeBoutEnBoutTest {

	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final String SOIREES = "/api/soirees";
	private static final String JOUEURS_MODIFIES = "joueurs-modifies";

	@LocalServerPort private int port;
	@Autowired private JsonMapper json;
	@Autowired private ConnexionsDesManettes connexions;
	@Autowired private SimpUserRegistry utilisateurs;
	private final WsWebSocketContainer conteneur = new WsWebSocketContainer();
	private final WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient(conteneur));
	private final List<StompSession> sessions = new ArrayList<>();
	private final HttpClient clientHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private final RestClient http = clientHttp(clientHttp);
	private SessionAnimateurHttp animateur;

	@AfterEach
	void fermerLesConnexions() {
		for (var session : sessions) {
			if (session.isConnected()) {
				session.disconnect();
			}
		}
		stomp.stop();
		conteneur.destroy();
		clientHttp.close();
	}

	@Test
	@Timeout(30)
	void unAnimateurConnecteOuvreSaSoireeEtLaSalleSuitLeJoueurDeSonArriveeASonRetour() throws Exception {
		animateur = SessionAnimateurHttp.ouvrir(port, json);
		Soiree soiree = creerEtOuvrir("Soirée du vendredi");
		Ecoute console = ecouter(Acces.console(animateur), "/topic/soirees/" + soiree.id() + "/console",
				soiree.route() + "/etat/console", "retrouverEtatConsole");
		Ecoute salle = ecouter(Acces.bearer(soiree.cleEcran()), "/topic/soirees/" + soiree.id() + "/salle",
				soiree.route() + "/etat/salle", "retrouverEtatSalle");
		assertThat(console.capture().path("vue").path("joueurs")).isEmpty();
		assertThat(salle.capture().path("vue").path("joueurs")).isEmpty();
		assertThat(salle.capture().path("vue").path("soiree").path("codeAcces").asString()).isEqualTo(soiree.code());

		Joueur lea = rejoindre(soiree, "Léa");
		for (Ecoute ecran : List.of(salle, console)) {
			JsonNode arrivee = ecran.suivant(JOUEURS_MODIFIES);
			assertThat(arrivee.path("donnees").path("joueurs")).containsExactly(lea.enLigne(true, json));
		}

		Ecoute manette = ecouter(Acces.bearer(lea.jeton()), "/topic/joueurs/" + lea.id(), soiree.route() + "/etat/manette",
				"retrouverEtatManette");
		assertThat(manette.capture().path("vue").path("joueurId").asString()).isEqualTo(lea.id().toString());
		JsonNode recu = verifier("envoyerBuzz", buzzer(soiree, lea), 200);
		assertThat(recu.path("joueurId").asString()).isEqualTo(lea.id().toString());
		assertThat(recu.path("pseudo").asString()).isEqualTo("Léa");
		for (Ecoute ecran : List.of(salle, console, manette)) {
			assertThat(ecran.suivant("buzz-recu").path("donnees").path("dernierBuzz")).isEqualTo(recu);
		}

		Soiree autre = creerEtOuvrir("Soirée du samedi");
		JsonNode refus = verifier("envoyerBuzz", buzzer(autre, lea), 404);
		assertThat(refus.path("code").asString()).isEqualTo("RESSOURCE_INTROUVABLE");

		manette.session().disconnect();
		for (Ecoute ecran : List.of(salle, console)) {
			JsonNode deconnexion = ecran.suivant(JOUEURS_MODIFIES);
			assertThat(deconnexion.path("donnees").path("joueurs")).containsExactly(lea.enLigne(false, json));
			assertThat(deconnexion.path("donnees").path("dernierBuzz")).isEqualTo(recu);
		}
		JsonNode captureSalle = verifier("retrouverEtatSalle", envoyer(HttpMethod.GET, soiree.route() + "/etat/salle",
				Acces.bearer(soiree.cleEcran()), null, null), 200);
		assertThat(captureSalle.path("vue").path("joueurs")).containsExactly(lea.enLigne(false, json));

		Ecoute retour = ecouter(Acces.bearer(lea.jeton()), "/topic/joueurs/" + lea.id(), soiree.route() + "/etat/manette",
				"retrouverEtatManette");
		for (Ecoute ecran : List.of(salle, console)) {
			assertThat(ecran.suivant(JOUEURS_MODIFIES).path("donnees").path("joueurs"))
					.containsExactly(lea.enLigne(true, json));
		}
		assertThat(retour.capture().path("vue").path("joueurs")).containsExactly(lea.enLigne(true, json));
	}

	/**
	 * Fin de la Soirée (#266) : la présence est oubliée, une Manette qui se ferme ensuite ne publie plus rien, et
	 * la capture rend la vue soiree-terminee, avec la présence constatée à la fin. Noé n'ouvre jamais de connexion STOMP : il reste
	 * connecté jusqu'à sa première connexion.
	 */
	@Test
	@Timeout(30)
	void aLaFinDeLaSoireeLaPresenceEstOublieeEtUneManetteQuiSeFermeNePublieRien() throws Exception {
		animateur = SessionAnimateurHttp.ouvrir(port, json);
		Soiree soiree = creerEtOuvrir("Soirée de clôture");
		verifier("ajouterManche", envoyer(HttpMethod.POST, soiree.route() + "/manches", Acces.console(animateur),
				Map.of("mode", "KARAOKE", "reglages", Map.of()), null), 201);
		Ecoute salle = ecouter(Acces.bearer(soiree.cleEcran()), "/topic/soirees/" + soiree.id() + "/salle",
				soiree.route() + "/etat/salle", "retrouverEtatSalle");
		Joueur lea = rejoindre(soiree, "Léa");
		Joueur sam = rejoindre(soiree, "Sam");
		Joueur noe = rejoindre(soiree, "Noé");
		Ecoute manetteDeLea = ecouter(Acces.bearer(lea.jeton()), "/topic/joueurs/" + lea.id(),
				soiree.route() + "/etat/manette", "retrouverEtatManette");
		ecouter(Acces.bearer(sam.jeton()), "/topic/joueurs/" + sam.id(), soiree.route() + "/etat/manette",
				"retrouverEtatManette").session().disconnect();
		assertThat(salle.jusqua(JOUEURS_MODIFIES, joueurs -> joueurs.equals(json.valueToTree(List.of(
				lea.enLigne(true, json), sam.enLigne(false, json), noe.enLigne(true, json)))))).isNotNull();

		verifier("piloterSoiree", envoyer(HttpMethod.POST, soiree.route() + "/commandes", Acces.console(animateur),
				Map.of("action", "LANCER"), UUID.randomUUID()), 200);
		verifier("piloterSoiree", envoyer(HttpMethod.POST, soiree.route() + "/commandes", Acces.console(animateur),
				Map.of("action", "TERMINER"), UUID.randomUUID()), 200);
		JsonNode fin = salle.jusqua("soiree-terminee", joueurs -> true);
		assertThat(fin.path("donnees").path("joueurs")).containsExactly(lea.enLigne(true, json),
				sam.enLigne(false, json), noe.enLigne(true, json));

		// La capture prend le verrou de la Soirée : la fin, sa publication et son oubli sont alors achevés.
		JsonNode apresLaFin = verifier("retrouverEtatSalle", envoyer(HttpMethod.GET, soiree.route() + "/etat/salle",
				Acces.bearer(soiree.cleEcran()), null, null), 200);
		assertThat(apresLaFin.path("vue").path("joueurs")).isEqualTo(fin.path("donnees").path("joueurs"));
		// La mémoire de la Soirée est rendue dès la fin : Sam, hors ligne à la fin, n'y figure plus.
		assertThat(connexions.estConnecte(sam.id())).isTrue();

		String nomDeLea = "JOUEUR:" + lea.id() + ":" + soiree.id();
		assertThat(utilisateurs.getUser(nomDeLea)).isNotNull();
		manetteDeLea.session().disconnect();
		await().atMost(Duration.ofSeconds(5)).until(() -> utilisateurs.getUser(nomDeLea) == null);
		// Attente d'absence, une fois la fermeture constatée par le serveur : aucun joueurs-modifies ne suit la fin.
		assertThat(salle.evenements().poll(1, TimeUnit.SECONDS)).isNull();
		JsonNode capture = verifier("retrouverEtatSalle", envoyer(HttpMethod.GET, soiree.route() + "/etat/salle",
				Acces.bearer(soiree.cleEcran()), null, null), 200);
		assertThat(capture.path("vue").path("joueurs")).isEqualTo(fin.path("donnees").path("joueurs"));
		assertThat(connexions.estConnecte(lea.id())).isTrue();
	}

	private Soiree creerEtOuvrir(String titre) throws Exception {
		Acces session = Acces.console(animateur);
		JsonNode creation = verifier("creerSoiree", envoyer(HttpMethod.POST, SOIREES, session,
				Map.of("titre", titre, "format", "SOLO", "pilotage", "MANUEL"), null), 201);
		String route = SOIREES + "/" + creation.path("id").asString();
		JsonNode ouverte = verifier("ouvrirSoiree", envoyer(HttpMethod.POST, route + "/ouverture", session, null,
				UUID.randomUUID()), 200);
		JsonNode ecran = verifier("creerAccesEcran", envoyer(HttpMethod.POST, route + "/acces-ecran", session, null,
				UUID.randomUUID()), 200);
		return new Soiree(UUID.fromString(creation.path("id").asString()), route,
				ouverte.path("codeAcces").asString(), ecran.path("cleEcran").asString());
	}

	private Joueur rejoindre(Soiree soiree, String pseudo) throws Exception {
		JsonNode arrivee = verifier("rejoindreSoiree", envoyer(HttpMethod.POST, SOIREES + "/rejoindre", Acces.AUCUN,
				Map.of("codeAcces", soiree.code(), "pseudo", pseudo), null), 201);
		return new Joueur(UUID.fromString(arrivee.path("joueur").path("id").asString()), pseudo,
				arrivee.path("jetonReconnexion").asString());
	}

	private ResponseEntity<String> buzzer(Soiree soiree, Joueur joueur) {
		return envoyer(HttpMethod.POST, soiree.route() + "/buzz", Acces.bearer(joueur.jeton()),
				Map.of("envoiId", UUID.randomUUID()), null);
	}

	/** Connexion, abonnement effectivement installé, puis capture raccordée à cet abonnement. */
	private Ecoute ecouter(Acces acces, String destination, String routeEtat, String operation) throws Exception {
		stomp.setMessageConverter(new JacksonJsonMessageConverter());
		StompSession session = stomp.connectAsync("ws://localhost:" + port + "/ws", acces.poigneeDeMain(),
				acces.connexion(), new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
		sessions.add(session);
		StompHeaders abonnement = new StompHeaders();
		abonnement.setDestination(destination);
		abonnement.setId(UUID.randomUUID().toString());
		BlockingQueue<JsonNode> evenements = new LinkedBlockingQueue<>();
		session.subscribe(abonnement, new StompFrameHandler() {
			@Override public Type getPayloadType(StompHeaders entetes) { return JsonNode.class; }
			@Override public void handleFrame(StompHeaders entetes, Object contenu) { evenements.add((JsonNode) contenu); }
		});
		String capture = routeEtat + "?abonnementId=" + abonnement.getId();
		var reponse = await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(5))
				.until(() -> envoyer(HttpMethod.GET, capture, acces, null, null), r -> r.getStatusCode().value() == 200);
		return new Ecoute(session, evenements, verifier(operation, reponse, 200));
	}

	private static RestClient clientHttp(HttpClient client) {
		var fabrique = new JdkClientHttpRequestFactory(client);
		fabrique.setReadTimeout(Duration.ofSeconds(10));
		return RestClient.builder().requestFactory(fabrique).build();
	}

	private ResponseEntity<String> envoyer(HttpMethod methode, String route, Acces acces, Object corps, UUID cle) {
		var requete = http.method(methode).uri("http://localhost:" + port + route)
				.headers(entetes -> entetes.addAll(acces.entetes()));
		if (cle != null) { requete.header("Idempotency-Key", cle.toString()); }
		if (corps != null) { requete.contentType(MediaType.APPLICATION_JSON).body(corps); }
		return requete.retrieve().onStatus(statut -> statut.isError(), (q, r) -> { }).toEntity(String.class);
	}

	private JsonNode verifier(String operation, ResponseEntity<String> reponse, int statut) throws Exception {
		assertThat(reponse.getStatusCode().value()).as("%s : %s", operation, reponse.getBody()).isEqualTo(statut);
		ContratApi.verifier(CONTRAT, operation, reponse);
		return json.readTree(reponse.getBody());
	}

	/** Identité d'un client : session de l'Animateur, Bearer d'Écran ou de Joueur, ou rien. */
	private record Acces(HttpHeaders entetes, WebSocketHttpHeaders poigneeDeMain, StompHeaders connexion) {
		static final Acces AUCUN = new Acces(new HttpHeaders(), new WebSocketHttpHeaders(), new StompHeaders());

		static Acces console(SessionAnimateurHttp session) {
			return new Acces(session.entetes(), session.poigneeDeMain(), session.connexionStomp());
		}

		static Acces bearer(String secret) {
			var entetes = new HttpHeaders();
			entetes.setBearerAuth(secret);
			var connexion = new StompHeaders();
			connexion.set(HttpHeaders.AUTHORIZATION, "Bearer " + secret);
			return new Acces(entetes, new WebSocketHttpHeaders(), connexion);
		}

		@Override
		public String toString() {
			return "Acces[***]";
		}
	}

	private record Ecoute(StompSession session, BlockingQueue<JsonNode> evenements, JsonNode capture) {
		JsonNode suivant(String type) throws InterruptedException {
			JsonNode evenement = evenements.poll(5, TimeUnit.SECONDS);
			assertThat(evenement).as("événement %s", type).isNotNull();
			assertThat(evenement.path("type").asString()).isEqualTo(type);
			return evenement;
		}

		/** Premier événement de ce type dont les Joueurs remplissent la condition ; les autres sont passés. */
		JsonNode jusqua(String type, Predicate<JsonNode> joueurs) throws InterruptedException {
			long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			JsonNode evenement;
			do {
				evenement = evenements.poll(limite - System.nanoTime(), TimeUnit.NANOSECONDS);
				assertThat(evenement).as("événement %s", type).isNotNull();
			} while (!type.equals(evenement.path("type").asString())
					|| !joueurs.test(evenement.path("donnees").path("joueurs")));
			return evenement;
		}
	}

	private record Soiree(UUID id, String route, String code, String cleEcran) { }

	private record Joueur(UUID id, String pseudo, String jeton) {
		JsonNode enLigne(boolean connecte, JsonMapper json) {
			return json.valueToTree(Map.of("id", id.toString(), "pseudo", pseudo, "connecte", connecte));
		}

		@Override
		public String toString() {
			return "Joueur[" + pseudo + "]";
		}
	}
}
