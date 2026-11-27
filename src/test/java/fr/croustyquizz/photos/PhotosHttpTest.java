package fr.croustyquizz.photos;

import static fr.croustyquizz.Attentes.PLAFOND;
import static fr.croustyquizz.Attentes.PLAFOND_SECONDES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.SessionAnimateurHttp;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.Accepte;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.HorlogeFactice;
import fr.croustyquizz.tempsreel.PublicationDeVues;
import fr.croustyquizz.tempsreel.RegistreAbonnements;
import fr.croustyquizz.tempsreel.VerrousSoiree;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, PhotosHttpTest.TempsDeTest.class})
class PhotosHttpTest {

	private static final String CONTRAT = "docs/api/openapi.yaml";
	private static final String CONTRAT_PROTOTYPE = "docs/api/prototype/openapi.yaml";
	private static final String ENVOYER = "envoyerPhoto";
	private static final String CONSULTER = "consulterPhoto";

	@LocalServerPort int port;
	@Autowired JsonMapper json;
	@Autowired HorlogeFactice horloge;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactions;
	@Autowired PublicationDeVues vues;
	@Autowired VerrousSoiree verrous;
	@Autowired RegistreAbonnements abonnements;
	@Autowired PhotosEnMemoire memoire;

	private final WsWebSocketContainer conteneur = new WsWebSocketContainer();
	private final SimpleAsyncTaskExecutor executeur = new SimpleAsyncTaskExecutor("photos-stomp-");
	private final List<WebSocketStompClient> clients = new ArrayList<>();
	private final List<StompSession> sessions = new ArrayList<>();
	/** Animateur du Lieu qui crée les Soirées : ses requêtes partent sans Bearer, avec sa session. */
	private SessionAnimateurHttp proprietaire;

	@BeforeEach
	void connecterLeProprietaire() throws Exception {
		proprietaire = SessionAnimateurHttp.ouvrir(port, json);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TempsDeTest {
		@Bean
		@Primary
		HorlogeFactice horlogePhotos() {
			return new HorlogeFactice();
		}
	}

	@AfterEach
	void fermerConnexions() {
		sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
		clients.forEach(WebSocketStompClient::stop);
		executeur.close();
		conteneur.destroy();
	}

	@Test
	void leJoueurEnvoieUnJpegQueSeulLAnimateurPeutLireAvantModeration() throws IOException {
		var soiree = ouvrirSoiree();
		String joueur = rejoindre(soiree);
		String animateur = animateur(soiree);
		String ecran = ouvrirEcran(soiree);
		byte[] jpeg = jpeg(8, 8);
		UUID cle = UUID.randomUUID();

		var envoi = verifier(ENVOYER, 201,
				envoyer(HttpMethod.POST, photos(soiree), joueur, cle, jpeg));
		String photo = photos(soiree) + "/" + envoi.path("photoId").asString();
		assertThat(verifier(ENVOYER, 201, envoyer(HttpMethod.POST, photos(soiree), joueur, cle, jpeg)))
				.isEqualTo(envoi);
		assertThat(verifier(ENVOYER, 409, envoyer(HttpMethod.POST, photos(soiree), joueur, cle, jpeg(9, 8)))
				.path("code").asString()).isEqualTo("ENVOI_REUTILISE");

		verifier(CONSULTER, 404, envoyer(HttpMethod.GET, photo, ecran, null, null));
		verifier(CONSULTER, 403, envoyer(HttpMethod.GET, photo, joueur, null, null));
		assertThat(verifierJpeg(envoyer(HttpMethod.GET, photo, animateur, null, null))).isEqualTo(jpeg);
	}

	@Test
	void lesPhotosPassentDansLOrdreDesAcceptationsHorsMancheEtSontPurgeesALaFin() throws Exception {
		var soiree = ouvrirSoiree();
		UUID soireeId = UUID.fromString(soiree.path("id").asString());
		var joueur = rejoindre(soiree);
		var animateur = animateur(soiree);
		var ecran = ouvrirEcran(soiree);
		var autreEcran = ouvrirEcran(soiree);
		var salle = abonner(soireeId, "salle", ecran);
		var deuxiemeSalle = abonner(soireeId, "salle", autreEcran);
		var console = abonner(soireeId, "console", animateur);
		byte[] jpeg = jpeg(8, 8);

		var premiere = deposer(soiree, joueur, jpeg);
		assertThat(publication(console, "photos-modifiees").path("photos").path("enAttente").get(0).path("pseudo").asString())
				.isEqualTo("Léa");
		var deuxieme = deposer(soiree, joueur, jpeg(9, 8));
		var rejetee = deposer(soiree, joueur, jpeg);
		assertThat(publication(console, "photos-modifiees").path("photos").path("enAttente")).hasSize(2);
		assertThat(publication(console, "photos-modifiees").path("photos").path("enAttente")).hasSize(3);
		verifier(CONSULTER, 404, envoyer(HttpMethod.GET, routePhoto(soiree, deuxieme), ecran, null, null));

		modere(soiree, deuxieme, animateur, true, UUID.randomUUID());
		JsonNode affiche = publication(salle, "photos-modifiees").path("photos");
		assertThat(affiche.path("photoId").asString()).isEqualTo(deuxieme);
		assertThat(affiche.path("echeance").asString()).isEqualTo(horloge.maintenant().plusSeconds(8).toString());
		assertThat(affiche.size()).isEqualTo(2);
		assertThat(publication(deuxiemeSalle, "photos-modifiees").path("photos")).isEqualTo(affiche);
		assertThat(capturer(soiree, "salle", autreEcran, deuxiemeSalle).path("photos")).isEqualTo(affiche);
		assertThat(verifierJpeg(envoyer(HttpMethod.GET, routePhoto(soiree, deuxieme), ecran, null, null)))
				.isEqualTo(jpeg(9, 8));

		UUID cleRefus = UUID.randomUUID();
		assertThat(modere(soiree, rejetee, animateur, false, cleRefus))
				.isEqualTo(modere(soiree, rejetee, animateur, false, cleRefus));
		verifier(CONSULTER, 404, envoyer(HttpMethod.GET, routePhoto(soiree, rejetee), animateur, null, null));

		horloge.avancer(Duration.ofSeconds(2));
		jdbc.update("update soiree set etat = 'EN_COURS', debutee_le = ? where id = ?",
				java.sql.Timestamp.from(horloge.maintenant()), soireeId);
		String manche = demarrerManche(soireeId, animateur);
		assertThat(publication(salle, "manche-demarree").path("photos").isNull()).isTrue();
		verifier(CONSULTER, 404, envoyer(HttpMethod.GET, routePhoto(soiree, deuxieme), ecran, null, null));
		modere(soiree, premiere, animateur, true, UUID.randomUUID());
		horloge.avancer(Duration.ofSeconds(10));
		assertThat(capturer(soiree, "salle", ecran, salle).path("photos").isNull()).isTrue();

		var pendante = deposer(soiree, joueur, jpeg);
		var enFile = deposer(soiree, joueur, jpeg);
		modere(soiree, enFile, animateur, true, UUID.randomUUID());
		UUID coupure = UUID.randomUUID();
		var coupees = verifier("reglerPhotos", 200, envoyer(HttpMethod.PUT, photos(soiree) + "/reglages",
				animateur, coupure, Map.of("ouvertes", false)));
		assertThat(verifier("reglerPhotos", 200, envoyer(HttpMethod.PUT, photos(soiree) + "/reglages",
				animateur, coupure, Map.of("ouvertes", false)))).isEqualTo(coupees);
		assertThat(refusEnvoi(soiree, joueur, jpeg, 409).path("code").asString()).isEqualTo("PHOTOS_COUPEES");

		verifier("piloterManche", 200, envoyer(HttpMethod.POST, route(soiree) + "/manches/" + manche + "/commandes",
				animateur, UUID.randomUUID(), Map.of("action", "TERMINER")));
		assertThat(publication(salle, "manche-terminee").path("photos").isNull()).isTrue();
		affiche = publication(salle, "photos-modifiees").path("photos");
		assertThat(affiche.path("photoId").asString()).isEqualTo(deuxieme);
		assertThat(affiche.path("echeance").asString()).isEqualTo(horloge.maintenant().plusSeconds(8).toString());
		horloge.avancer(Duration.ofSeconds(7));
		assertThat(capturer(soiree, "salle", ecran, salle).path("photos")).isEqualTo(affiche);
		horloge.avancer(Duration.ofSeconds(1));
		assertThat(publication(salle, "photos-modifiees").path("photos").path("photoId").asString()).isEqualTo(premiere);
		verifier(CONSULTER, 404, envoyer(HttpMethod.GET, routePhoto(soiree, deuxieme), animateur, null, null));

		terminerSoiree(soireeId);
		assertThat(publication(salle, "soiree-terminee").path("photos").isNull()).isTrue();
		verifier(CONSULTER, 404, envoyer(HttpMethod.GET, routePhoto(soiree, premiere), ecran, null, null));
		verifier(CONSULTER, 404, envoyer(HttpMethod.GET, routePhoto(soiree, pendante), animateur, null, null));
		verifier(CONSULTER, 404, envoyer(HttpMethod.GET, routePhoto(soiree, enFile), animateur, null, null));
		assertThat(refusEnvoi(soiree, joueur, jpeg, 409).path("code").asString()).isEqualTo("ETAT_INCOMPATIBLE");
		horloge.avancer(Duration.ofSeconds(8));
		assertThat(salle.messages()).isEmpty();
	}

	@Test
	void lesBornesDeJpegEtDeFileLaissentUneNouvelleTentativePossible() throws IOException {
		var soiree = ouvrirSoiree();
		var joueur = rejoindre(soiree);
		var animateur = animateur(soiree);
		byte[] petite = jpeg(8, 8);
		assertThat(verifier(ENVOYER, 422, envoyer(HttpMethod.POST, photos(soiree), joueur, UUID.randomUUID(),
				Map.of("format", "PNG"))).path("code").asString()).isEqualTo("DONNEES_INVALIDES");
		assertThat(refusEnvoi(soiree, joueur, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xff, (byte) 0xd9}, 422)
				.path("code").asString()).isEqualTo("DONNEES_INVALIDES");
		assertThat(refusEnvoi(soiree, joueur, jpeg(16, 2049), 422).path("detail").asString()).contains("2048 pixels");
		assertThat(refusEnvoi(soiree, joueur, avecCommentaires(petite, 1_500_001), 422).path("detail").asString())
				.contains("1,5 Mo");
		var photo = deposer(soiree, joueur, avecCommentaires(jpeg(2048, 16), 1_500_000));
		for (int i = 0; i < 2; i++) {
			deposer(soiree, joueur, petite);
		}
		var refusJoueur = refusEnvoi(soiree, joueur, petite, 409);
		assertThat(refusJoueur.path("code").asString()).isEqualTo("LIMITE_ATTEINTE");
		assertThat(refusJoueur.path("detail").asString()).contains("trois photos");
		for (int i = 0; i < 9; i++) {
			String autreJoueur = rejoindre(soiree, "Joueur " + i);
			for (int j = 0; j < 3; j++) {
				deposer(soiree, autreJoueur, petite);
			}
		}
		String dernierJoueur = rejoindre(soiree, "Dernier Joueur");
		var refusSoiree = refusEnvoi(soiree, dernierJoueur, petite, 409);
		assertThat(refusSoiree.path("code").asString()).isEqualTo("LIMITE_ATTEINTE");
		assertThat(refusSoiree.path("detail").asString()).contains("trente photos");
		modere(soiree, photo, animateur, false, UUID.randomUUID());
		assertThat(deposer(soiree, dernierJoueur, petite)).isNotEmpty();
	}

	@Test
	void auDelaDeQuatreEnvoisEnCoursLeServeurRepondIndisponibleSansRecu() throws IOException {
		var soiree = ouvrirSoiree();
		var joueur = rejoindre(soiree);
		byte[] jpeg = jpeg(8, 8);
		UUID cle = UUID.randomUUID();
		int tenus = 0;
		try {
			while (memoire.entamerEnvoi()) {
				tenus++;
			}
			assertThat(tenus).isEqualTo(PhotosEnMemoire.ENVOIS_SIMULTANES_MAX);
			var refus = verifier(ENVOYER, 503, envoyer(HttpMethod.POST, photos(soiree), joueur, cle, jpeg));
			assertThat(refus.path("code").asString()).isEqualTo("SERVICE_INDISPONIBLE");
		} finally {
			for (; tenus > 0; tenus--) {
				memoire.terminerEnvoi();
			}
		}
		assertThat(verifier(ENVOYER, 201, envoyer(HttpMethod.POST, photos(soiree), joueur, cle, jpeg))
				.path("envoiId").asString()).isEqualTo(cle.toString());
	}

	private String deposer(JsonNode soiree, String joueur, byte[] jpeg) throws IOException {
		return verifier(ENVOYER, 201, envoyer(HttpMethod.POST, photos(soiree), joueur, UUID.randomUUID(), jpeg))
				.path("photoId").asString();
	}

	private JsonNode refusEnvoi(JsonNode soiree, String joueur, byte[] jpeg, int statut) throws IOException {
		return verifier(ENVOYER, statut, envoyer(HttpMethod.POST, photos(soiree), joueur, UUID.randomUUID(), jpeg));
	}

	private JsonNode modere(JsonNode soiree, String photoId, String animateur, boolean acceptee, UUID cle) throws IOException {
		return verifier("modererPhoto", 200, envoyer(HttpMethod.POST, routePhoto(soiree, photoId) + "/moderation",
				animateur, cle, Map.of("acceptee", acceptee)));
	}

	private static String routePhoto(JsonNode soiree, String photoId) {
		return photos(soiree) + "/" + photoId;
	}

	private String demarrerManche(UUID soiree, String animateur) throws IOException {
		String racine = "/api/test/soirees/" + soiree + "/manches";
		String manche = verifier(CONTRAT_PROTOTYPE, "creerMancheDeTest", 201, envoyer(HttpMethod.POST, racine, animateur,
				null, Map.of("mode", "KARAOKE", "reglages", Map.of()))).path("mancheId").asString();
		verifier(CONTRAT_PROTOTYPE, "demarrerMancheDeTest", 200,
				envoyer(HttpMethod.POST, racine + "/" + manche + "/demarrer", animateur, UUID.randomUUID(), null));
		return manche;
	}

	private void terminerSoiree(UUID soireeId) {
		// #12 implémentera la commande de fin. Reproduit ici le commit et sa publication.
		verrous.executer(soireeId, () -> {
			new TransactionTemplate(transactions).executeWithoutResult(statut -> {
				jdbc.update("update soiree set etat = 'TERMINEE', terminee_le = ? where id = ?",
						java.sql.Timestamp.from(horloge.maintenant()), soireeId);
				vues.publier(soireeId, new Accepte(null, Set.of(Destination.SALLE, Destination.CONSOLE,
						Destination.TOUTES_LES_MANETTES), "soiree-terminee"));
			});
			return null;
		});
	}

	private Abonnement abonner(UUID soireeId, String ecran, String jeton) throws Exception {
		var websocket = new StandardWebSocketClient(conteneur);
		websocket.setTaskExecutor(executeur);
		var client = new WebSocketStompClient(websocket);
		clients.add(client);
		client.setMessageConverter(new JacksonJsonMessageConverter());
		var connexion = jeton == null ? proprietaire.connexionStomp() : new StompHeaders();
		if (jeton != null) {
			connexion.set("Authorization", "Bearer " + jeton);
		}
		var poigneeDeMain = jeton == null ? proprietaire.poigneeDeMain() : new WebSocketHttpHeaders();
		var session = client.connectAsync("ws://localhost:" + port + "/ws", poigneeDeMain,
				connexion, new StompSessionHandlerAdapter() { })
				.get(PLAFOND_SECONDES, TimeUnit.SECONDS);
		sessions.add(session);
		String destination = "/topic/soirees/" + soireeId + "/" + ecran;
		UUID id = UUID.randomUUID();
		var entetes = new StompHeaders();
		entetes.setId(id.toString());
		entetes.setDestination(destination);
		BlockingQueue<JsonNode> messages = new ArrayBlockingQueue<>(32);
		session.subscribe(entetes, new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) { return Map.class; }
			@Override
			public void handleFrame(StompHeaders headers, Object corps) { messages.add(json.valueToTree(corps)); }
		});
		await().atMost(PLAFOND).until(() -> abonnements.estInstalle(id, destination));
		return new Abonnement(id, messages);
	}

	private static JsonNode publication(Abonnement abonnement, String type) throws InterruptedException {
		JsonNode message = abonnement.messages().poll(PLAFOND_SECONDES, TimeUnit.SECONDS);
		assertThat(message).isNotNull();
		assertThat(message.path("type").asString()).isEqualTo(type);
		return message.path("donnees");
	}

	private JsonNode capturer(JsonNode soiree, String ecran, String jeton, Abonnement abonnement) throws IOException {
		return verifier("retrouverEtatSalle", 200,
				envoyer(HttpMethod.GET, route(soiree) + "/etat/" + ecran + "?abonnementId=" + abonnement.id(), jeton, null, null))
				.path("vue");
	}

	private record Abonnement(UUID id, BlockingQueue<JsonNode> messages) { }

	private JsonNode ouvrirSoiree() throws IOException {
		var creation = verifier("creerSoiree", 201, envoyer(HttpMethod.POST, "/api/soirees", null, null,
				Map.of("titre", "Soirée photos", "format", "SOLO", "pilotage", "MANUEL")));
		return verifier("ouvrirSoiree", 200, envoyer(HttpMethod.POST, route(creation) + "/ouverture", null, null, null));
	}

	private String rejoindre(JsonNode soiree) throws IOException {
		return rejoindre(soiree, "Léa");
	}

	private String rejoindre(JsonNode soiree, String pseudo) throws IOException {
		return verifier("rejoindreSoiree", 201, envoyer(HttpMethod.POST, "/api/soirees/rejoindre", null, null,
				Map.of("codeAcces", soiree.path("codeAcces").asString(), "pseudo", pseudo)))
				.path("jetonReconnexion").asString();
	}

	private String ouvrirEcran(JsonNode soiree) throws IOException {
		return verifier("creerAccesEcran", 200, envoyer(HttpMethod.POST, route(soiree) + "/acces-ecran", null, null, null))
				.path("cleEcran").asString();
	}

	/** Aucun Bearer pour l'Animateur : la requête part avec la session du propriétaire de la Soirée. */
	private static String animateur(JsonNode soiree) {
		return null;
	}

	private static String route(JsonNode soiree) {
		return "/api/soirees/" + soiree.path("id").asString();
	}

	private static String photos(JsonNode soiree) {
		return route(soiree) + "/photos";
	}

	private ResponseEntity<byte[]> envoyer(HttpMethod methode, String route, String jeton, UUID cle, Object corps) {
		var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route);
		if (jeton != null) {
			requete.header("Authorization", "Bearer " + jeton);
		} else {
			requete.headers(entetes -> entetes.addAll(proprietaire.entetes()));
		}
		if (cle != null) {
			requete.header("Idempotency-Key", cle.toString());
		}
		if (corps instanceof byte[] jpeg) {
			requete.contentType(MediaType.IMAGE_JPEG).body(jpeg);
		} else if (corps != null) {
			requete.contentType(MediaType.APPLICATION_JSON).body(corps);
		}
		return requete.retrieve().onStatus(s -> s.isError(), (q, r) -> { }).toEntity(byte[].class);
	}

	private JsonNode verifier(String operation, int statut, ResponseEntity<byte[]> reponse) throws IOException {
		return verifier(CONTRAT, operation, statut, reponse);
	}

	private JsonNode verifier(String contrat, String operation, int statut, ResponseEntity<byte[]> reponse) throws IOException {
		assertThat(reponse.getStatusCode().value()).isEqualTo(statut);
		assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("no-store");
		String corps = new String(reponse.getBody(), java.nio.charset.StandardCharsets.UTF_8);
		ContratApi.verifier(contrat, operation, statut, reponse.getHeaders().getFirst("Content-Type"), corps);
		return json.readTree(corps);
	}

	private static byte[] verifierJpeg(ResponseEntity<byte[]> reponse) throws IOException {
		assertThat(reponse.getStatusCode().value()).isEqualTo(200);
		assertThat(reponse.getHeaders().getCacheControl()).isEqualTo("no-store");
		byte[] corps = reponse.getBody();
		ContratApi.verifier(CONTRAT, CONSULTER, 200, reponse.getHeaders().getFirst("Content-Type"),
				java.util.Base64.getEncoder().encodeToString(corps));
		return corps;
	}

	private static byte[] avecCommentaires(byte[] jpeg, int taille) {
		var octets = java.nio.ByteBuffer.allocate(taille);
		octets.put(jpeg, 0, 2);
		int restant = taille - jpeg.length;
		while (restant > 0) {
			int segment = Math.min(60_000, restant);
			octets.put((byte) 0xff).put((byte) 0xfe).putShort((short) (segment - 2));
			octets.position(octets.position() + segment - 4);
			restant -= segment;
		}
		octets.put(jpeg, 2, jpeg.length - 2);
		return octets.array();
	}

	private static byte[] jpeg(int largeur, int hauteur) throws IOException {
		var octets = new ByteArrayOutputStream();
		try (var flux = new MemoryCacheImageOutputStream(octets)) {
			ImageIO.write(new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB), "jpeg", flux);
		}
		return octets.toByteArray();
	}

}
