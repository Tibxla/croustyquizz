package fr.croustyquizz.tempsreel;

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
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.karaoke.Chanson;
import fr.croustyquizz.karaoke.ContenuKaraoke;
import fr.croustyquizz.karaoke.EtatParoles;
import fr.croustyquizz.karaoke.IdentifiantsChanson;
import fr.croustyquizz.karaoke.LigneParoles;
import fr.croustyquizz.karaoke.SourceDeParoles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Délégation (#67) de bout en bout : HTTP, PostgreSQL, commit puis STOMP, sur une Soirée enregistrée. Deux
 * pilotes agissent sur la même File d'attente : l'Animateur (identité de démonstration, en attendant #5) et
 * Alizé, Joueuse déléguée avec son vrai jeton de reconnexion. Exige Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, DelegationTempsReelTest.SourceLocale.class})
class DelegationTempsReelTest {
	private static final String CONTRAT = "docs/api/openapi.yaml";

	@LocalServerPort int port;
	@Autowired JsonMapper json;
	@Autowired JdbcTemplate jdbc;
	@Autowired RegistreAbonnements abonnements;

	final WsWebSocketContainer conteneur = new WsWebSocketContainer();
	final SimpleAsyncTaskExecutor executeur = new SimpleAsyncTaskExecutor("delegation-stomp-");
	final List<WebSocketStompClient> clients = new ArrayList<>();
	final List<StompSession> sessions = new ArrayList<>();

	String soiree;
	String animateur;
	JsonNode alize;
	JsonNode basile;

	@TestConfiguration(proxyBeanMethods = false)
	static class SourceLocale {
		@Bean @Primary SourceDeParoles sourceLocale() {
			return new SourceDeParoles() {
				public List<Chanson> rechercher(String recherche) { return List.of(contenu(1).chanson()); }
				public java.util.Optional<ContenuKaraoke> charger(long id) { return java.util.Optional.of(contenu(id)); }
			};
		}
		static ContenuKaraoke contenu(long id) {
			return new ContenuKaraoke(new Chanson(IdentifiantsChanson.encoder(id), id, "Chanson " + id, "Groupe fictif",
					null, 180_000L, EtatParoles.SYNCHRONISEES), List.of(new LigneParoles(1_000, "Première ligne")));
		}
	}

	@BeforeEach void ouvrirUneSoireeAvecDeuxJoueurs() {
		soiree = json.readTree(envoyer(HttpMethod.POST, "/api/soirees", null,
				Map.of("titre", "Soirée de la Délégation", "format", "SOLO", "pilotage", "MANUEL"), null).getBody())
				.path("id").asString();
		assertThat(envoyer(HttpMethod.POST, racine() + "/ouverture", null, null, UUID.randomUUID())
				.getStatusCode().is2xxSuccessful()).isTrue();
		animateur = "demo:ANIMATEUR:" + soiree + ":" + UUID.randomUUID();
		alize = rejoindre("Alizé");
		basile = rejoindre("Basile");
	}

	@AfterEach void fermer() {
		sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
		clients.forEach(WebSocketStompClient::stop);
		executeur.close();
		conteneur.destroy();
	}

	@Test void leDelegueEtLAnimateurPilotentLaMemeFileEtLeRetraitVautDesLaCommandeSuivante() throws Exception {
		String manche = lancerKaraoke();
		String passageBasile = inscrire(manche, basile, 1);
		String passageAlize = inscrire(manche, alize, 2);
		var console = abonner(animateur, Canaux.console(UUID.fromString(soiree)), "console");
		var manetteAlize = abonner(jeton(alize), Canaux.joueur(id(alize)), "manette");
		var manetteBasile = abonner(jeton(basile), Canaux.joueur(id(basile)), "manette");
		// L'Animateur décide sur cette vue : le Passage de Basile en tête, compteur 0.
		var vueAnimateur = capturer(console).path("vue");
		assertThat(vueAnimateur.at("/karaoke/passagesEnAttente/0/passageId").asString()).isEqualTo(passageBasile);

		var attribution = verifier("attribuerDelegation", 200, envoyer(HttpMethod.PUT, delegation(alize), animateur,
				Map.of("pouvoirs", List.of("PHOTOS", "FILE_ATTENTE")), null));
		assertThat(attribution.path("pseudo").asString()).isEqualTo("Alizé");
		assertThat(attribution.path("pouvoirs").toString()).isEqualTo("[\"FILE_ATTENTE\",\"PHOTOS\"]");
		var recue = evenement(manetteAlize, "delegation-modifiee");
		assertThat(recue.at("/delegation/pouvoirs").toString()).isEqualTo("[\"FILE_ATTENTE\",\"PHOTOS\"]");
		assertThat(recue.at("/delegation/fileAttente/passagesEnAttente")).hasSize(2);
		assertThat(recue.at("/delegation/fileAttente/peutAppeler").asBoolean()).isTrue();
		assertThat(recue.at("/delegation/photos/enAttente")).isEmpty();
		assertThat(recue.path("delegation").has("musiqueAttente")).isFalse();
		assertThat(recue.toString()).doesNotContain("fichierLocal", "cleEcran", "jeton", "\"lecture\"");
		assertThat(evenement(console, "delegation-modifiee").at("/delegations/0/joueurId").asString()).isEqualTo(id(alize).toString());
		assertThat(capturer(manetteBasile).at("/vue/delegation").isNull()).isTrue();
		assertThat(manetteBasile.messages()).isEmpty();
		// Même ensemble : succès sans publication.
		verifier("attribuerDelegation", 200, envoyer(HttpMethod.PUT, delegation(alize), animateur,
				Map.of("pouvoirs", List.of("FILE_ATTENTE", "PHOTOS")), null));

		var appel = verifier("appelerPassage", 200, envoyer(HttpMethod.POST, routeManche(manche) + "/passages/" + passageBasile
				+ "/appel", jeton(alize), Map.of("numeroModification", 0), UUID.randomUUID()));
		String etape = appel.path("etapeId").asString();
		assertThat(evenement(manetteAlize, "passage-appele").at("/delegation/fileAttente/courant/etapeId").asString()).isEqualTo(etape);
		verifier("piloterManche", 200, envoyer(HttpMethod.POST, routeManche(manche) + "/commandes", jeton(alize),
				Map.of("action", "PASSER_ETAPE", "etapeId", etape), UUID.randomUUID()));
		var apresSaut = evenement(manetteAlize, "passage-saute");
		assertThat(apresSaut.at("/delegation/fileAttente/courant/etat").asString()).isEqualTo("SAUTE");
		assertThat(evenement(manetteBasile, "passage-appele").path("delegation").isNull()).isTrue();
		assertThat(evenement(manetteBasile, "passage-saute").path("delegation").isNull()).isTrue();
		evenement(console, "passage-appele");
		var consoleApresSaut = evenement(console, "passage-saute");

		// L'Animateur, sur sa vue d'avant, appelle le même Passage : périmé, sans effet ni publication.
		var perimee = verifier("appelerPassage", 409, envoyer(HttpMethod.POST, routeManche(manche) + "/passages/"
				+ vueAnimateur.at("/karaoke/passagesEnAttente/0/passageId").asString() + "/appel", animateur,
				Map.of("numeroModification", vueAnimateur.at("/karaoke/passagesEnAttente/0/numeroModification").asLong()),
				UUID.randomUUID()));
		assertThat(perimee.path("code").asString()).isEqualTo("COMMANDE_PERIMEE");
		assertThat(perimee.path("detail").asString()).isNotBlank();
		assertThat(capturer(console).path("vue")).isEqualTo(consoleApresSaut);

		// Hors de sa liste : ni fin de Manche, ni Musique d'attente, ni retrait de Passage, ni réglage des photos.
		assertThat(verifier("piloterManche", 403, envoyer(HttpMethod.POST, routeManche(manche) + "/commandes", jeton(alize),
				Map.of("action", "TERMINER"), UUID.randomUUID())).path("code").asString()).isEqualTo("ACCES_REFUSE");
		verifier("reglerMusiqueDAttente", 403, envoyer(HttpMethod.PUT, racine() + "/musique-attente", jeton(alize),
				Map.of("etat", "COUPEE"), null));
		verifier("retirerPassage", 403, envoyer(HttpMethod.POST, routeManche(manche) + "/passages/" + passageAlize + "/retrait",
				jeton(alize), Map.of("numeroModification", 0), UUID.randomUUID()));
		verifier("reglerPhotos", 403, envoyer(HttpMethod.PUT, racine() + "/photos/reglages", jeton(alize),
				Map.of("ouvertes", false), UUID.randomUUID()));
		verifier("attribuerDelegation", 403, envoyer(HttpMethod.PUT, delegation(basile), jeton(alize),
				Map.of("pouvoirs", List.of("FILE_ATTENTE")), null));
		assertThat(capturer(console).path("vue")).isEqualTo(consoleApresSaut);

		// Retrait : publié à Alizé et à la Console seulement, puis la commande suivante d'Alizé est refusée.
		verifier("retirerDelegation", 204, envoyer(HttpMethod.DELETE, delegation(alize), animateur, null, null));
		assertThat(evenement(manetteAlize, "delegation-modifiee").path("delegation").isNull()).isTrue();
		assertThat(evenement(console, "delegation-modifiee").path("delegations")).isEmpty();
		verifier("retirerDelegation", 204, envoyer(HttpMethod.DELETE, delegation(alize), animateur, null, null));
		var refus = verifier("definirPrioritePassage", 403, envoyer(HttpMethod.PUT, routeManche(manche) + "/file-attente/priorite",
				jeton(alize), Map.of("passage", Map.of("passageId", passageAlize, "numeroModification", 0), "prioritaire", true),
				UUID.randomUUID()));
		assertThat(refus.path("code").asString()).isEqualTo("ACCES_REFUSE");
		assertThat(refus.path("detail").asString()).isNotBlank();
		assertThat(capturer(manetteAlize).at("/vue/delegation").isNull()).isTrue();
		assertThat(manetteAlize.messages()).isEmpty();
		assertThat(manetteBasile.messages()).isEmpty();
	}

	@Test void deuxPilotesConcurrentsNeFontQuUnAppel() throws Exception {
		String manche = lancerKaraoke();
		String passage = inscrire(manche, basile, 1);
		verifier("attribuerDelegation", 200, envoyer(HttpMethod.PUT, delegation(alize), animateur,
				Map.of("pouvoirs", List.of("FILE_ATTENTE")), null));
		var depart = new CountDownLatch(1);
		String route = routeManche(manche) + "/passages/" + passage + "/appel";
		try (var pilotes = Executors.newFixedThreadPool(2)) {
			List<Future<ResponseEntity<String>>> reponses = new ArrayList<>();
			for (String pilote : List.of(animateur, jeton(alize))) {
				reponses.add(pilotes.submit(() -> {
					depart.await();
					return envoyer(HttpMethod.POST, route, pilote, Map.of("numeroModification", 0), UUID.randomUUID());
				}));
			}
			depart.countDown();
			List<Integer> statuts = new ArrayList<>();
			for (var reponse : reponses) {
				var resultat = reponse.get(10, TimeUnit.SECONDS);
				ContratApi.verifier(CONTRAT, "appelerPassage", resultat);
				statuts.add(resultat.getStatusCode().value());
			}
			assertThat(statuts).containsExactlyInAnyOrder(200, 409);
		}
		var console = json.readTree(envoyer(HttpMethod.GET, racine() + "/etat/console", animateur, null, null).getBody());
		assertThat(console.at("/vue/karaoke/courant/passageId").asString()).isEqualTo(passage);
		assertThat(console.at("/vue/karaoke/courant/numeroModification").asLong()).isEqualTo(1);
	}

	@Test void laMusiqueEtLesPhotosSuiventLaManetteDelegueeSansSecretDeConsole() throws Exception {
		var manetteAlize = abonner(jeton(alize), Canaux.joueur(id(alize)), "manette");
		var manetteBasile = abonner(jeton(basile), Canaux.joueur(id(basile)), "manette");
		verifier("attribuerDelegation", 200, envoyer(HttpMethod.PUT, delegation(alize), animateur,
				Map.of("pouvoirs", List.of("MUSIQUE_ATTENTE", "PHOTOS")), null));
		var recue = evenement(manetteAlize, "delegation-modifiee");
		assertThat(recue.at("/delegation/musiqueAttente/etat").asString()).isEqualTo("JOUE");
		assertThat(recue.path("delegation").has("fileAttente")).isFalse();

		verifier("reglerMusiqueDAttente", 200, envoyer(HttpMethod.PUT, racine() + "/musique-attente", jeton(alize),
				Map.of("etat", "COUPEE"), null));
		assertThat(evenement(manetteAlize, "musique-attente-modifiee").at("/delegation/musiqueAttente/etat").asString())
				.isEqualTo("COUPEE");

		byte[] photo = jpeg();
		var envoi = verifier("envoyerPhoto", 201, envoyerPhoto(jeton(basile), photo));
		String photoId = envoi.path("photoId").asString();
		var enAttente = evenement(manetteAlize, "photos-modifiees");
		assertThat(enAttente.at("/delegation/photos/enAttente/0/pseudo").asString()).isEqualTo("Basile");
		assertThat(enAttente.toString()).doesNotContain("/9j/");
		verifier("consulterPhoto", 403, envoyer(HttpMethod.GET, racine() + "/photos/" + photoId, jeton(basile), null, null));
		var lue = RestClient.create("http://localhost:" + port).get().uri(racine() + "/photos/" + photoId)
				.header("Authorization", "Bearer " + jeton(alize)).retrieve().toEntity(byte[].class);
		assertThat(lue.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
		assertThat(lue.getBody()).isEqualTo(photo);

		verifier("modererPhoto", 200, envoyer(HttpMethod.POST, racine() + "/photos/" + photoId + "/moderation", jeton(alize),
				Map.of("acceptee", false), UUID.randomUUID()));
		assertThat(evenement(manetteAlize, "photos-modifiees").at("/delegation/photos/enAttente")).isEmpty();
		await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1)).until(() -> manetteBasile.messages().isEmpty());
	}

	@Test void lAttributionNeVaQuAUnJoueurPresentDUneSoireeEnregistreeEtDisparaitALaFin() throws Exception {
		for (var invalide : List.of(List.of(), List.of("EXCLUSION"), List.of("PHOTOS", "PHOTOS"))) {
			assertThat(verifier("attribuerDelegation", 400, envoyer(HttpMethod.PUT, delegation(alize), animateur,
					Map.of("pouvoirs", invalide), null)).path("code").asString()).isEqualTo("REQUETE_INVALIDE");
		}
		verifier("attribuerDelegation", 400, envoyer(HttpMethod.PUT, delegation(alize), animateur,
				Map.of("pouvoirs", List.of("PHOTOS"), "admin", true), null));
		verifier("attribuerDelegation", 404, envoyer(HttpMethod.PUT, racine() + "/delegations/" + UUID.randomUUID(), animateur,
				Map.of("pouvoirs", List.of("PHOTOS")), null));
		verifier("retirerDelegation", 404, envoyer(HttpMethod.DELETE, racine() + "/delegations/" + UUID.randomUUID(), animateur,
				null, null));
		UUID demonstration = UUID.randomUUID();
		verifier("attribuerDelegation", 404, envoyer(HttpMethod.PUT, "/api/soirees/" + demonstration + "/delegations/"
				+ FournisseurCommunDemonstration.joueurId(demonstration), "demo:ANIMATEUR:" + demonstration + ":" + UUID.randomUUID(),
				Map.of("pouvoirs", List.of("PHOTOS")), null));

		verifier("attribuerDelegation", 200, envoyer(HttpMethod.PUT, delegation(alize), animateur,
				Map.of("pouvoirs", List.of("MUSIQUE_ATTENTE")), null));
		verifier("attribuerDelegation", 200, envoyer(HttpMethod.PUT, delegation(basile), animateur,
				Map.of("pouvoirs", List.of("PHOTOS")), null));
		var console = verifier("retrouverEtatConsole", 200, envoyer(HttpMethod.GET, racine() + "/etat/console", animateur, null, null));
		assertThat(console.at("/vue/delegations")).hasSize(2);
		assertThat(console.at("/vue/delegations/0/pseudo").asString()).isEqualTo("Alizé");

		lancerKaraoke();
		verifier("piloterSoiree", 200, envoyer(HttpMethod.POST, racine() + "/commandes", animateur,
				Map.of("action", "TERMINER"), UUID.randomUUID()));
		await().atMost(Duration.ofSeconds(3)).until(() -> jdbc.queryForObject(
				"select count(*) from tempsreel_delegation where soiree_id = ?::uuid", Integer.class, soiree) == 0);
		var manette = verifier("retrouverEtatManette", 200, envoyer(HttpMethod.GET, racine() + "/etat/manette", jeton(alize), null, null));
		assertThat(manette.at("/vue/delegation").isNull()).isTrue();
		verifier("reglerMusiqueDAttente", 403, envoyer(HttpMethod.PUT, racine() + "/musique-attente", jeton(alize),
				Map.of("etat", "COUPEE"), null));
		assertThat(verifier("attribuerDelegation", 409, envoyer(HttpMethod.PUT, delegation(alize), animateur,
				Map.of("pouvoirs", List.of("PHOTOS")), null)).path("code").asString()).isEqualTo("ETAT_INCOMPATIBLE");
	}

	// Outils du parcours

	String lancerKaraoke() throws Exception {
		var creation = verifier("ajouterManche", 201, envoyer(HttpMethod.POST, racine() + "/manches", animateur,
				Map.of("mode", "KARAOKE", "reglages", Map.of()), null));
		verifier("piloterSoiree", 200, envoyer(HttpMethod.POST, racine() + "/commandes", animateur,
				Map.of("action", "LANCER"), UUID.randomUUID()));
		return creation.path("id").asString();
	}

	String inscrire(String manche, JsonNode joueur, long chanson) throws Exception {
		return verifier("inscrirePassage", 201, envoyer(HttpMethod.POST, routeManche(manche) + "/passages", jeton(joueur),
				Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(chanson)), null))
				.path("passageId").asString();
	}

	JsonNode rejoindre(String pseudo) {
		String code = json.readTree(envoyer(HttpMethod.GET, racine(), null, null, null).getBody()).path("codeAcces").asString();
		var arrivee = envoyer(HttpMethod.POST, "/api/soirees/rejoindre", null, Map.of("codeAcces", code, "pseudo", pseudo), null);
		assertThat(arrivee.getStatusCode().value()).isEqualTo(201);
		return json.readTree(arrivee.getBody());
	}

	String racine() { return "/api/soirees/" + soiree; }
	String routeManche(String manche) { return racine() + "/manches/" + manche; }
	String delegation(JsonNode joueur) { return racine() + "/delegations/" + id(joueur); }
	static String jeton(JsonNode joueur) { return joueur.path("jetonReconnexion").asString(); }
	static UUID id(JsonNode joueur) { return UUID.fromString(joueur.path("joueur").path("id").asString()); }

	JsonNode verifier(String operation, int statut, ResponseEntity<String> reponse) throws IOException {
		ContratApi.verifier(CONTRAT, operation, reponse);
		assertThat(reponse.getStatusCode().value()).as(operation + " : " + reponse.getBody()).isEqualTo(statut);
		return reponse.getBody() == null ? json.nullNode() : json.readTree(reponse.getBody());
	}

	ResponseEntity<String> envoyer(HttpMethod methode, String route, String secret, Object corps, UUID cle) {
		var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route);
		if (secret != null) { requete.header("Authorization", "Bearer " + secret); }
		if (cle != null) { requete.header("Idempotency-Key", cle.toString()); }
		if (corps != null) { requete.contentType(MediaType.APPLICATION_JSON).body(corps); }
		return requete.retrieve().onStatus(s -> s.isError(), (q, r) -> { }).toEntity(String.class);
	}

	ResponseEntity<String> envoyerPhoto(String secret, byte[] photo) {
		return RestClient.create("http://localhost:" + port).post().uri(racine() + "/photos")
				.header("Authorization", "Bearer " + secret).header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.IMAGE_JPEG).body(photo)
				.retrieve().onStatus(s -> s.isError(), (q, r) -> { }).toEntity(String.class);
	}

	static byte[] jpeg() throws IOException {
		var octets = new ByteArrayOutputStream();
		try (var flux = new MemoryCacheImageOutputStream(octets)) {
			ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpeg", flux);
		}
		return octets.toByteArray();
	}

	record Abonnement(String secret, String destination, String ecran, UUID id, BlockingQueue<JsonNode> messages) { }

	Abonnement abonner(String secret, String destination, String ecran) throws Exception {
		var websocket = new StandardWebSocketClient(conteneur);
		websocket.setTaskExecutor(executeur);
		var client = new WebSocketStompClient(websocket);
		clients.add(client);
		client.setMessageConverter(new JacksonJsonMessageConverter());
		var connexion = new StompHeaders();
		connexion.set("Authorization", "Bearer " + secret);
		var session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connexion,
				new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
		sessions.add(session);
		UUID id = UUID.randomUUID();
		var entetes = new StompHeaders();
		entetes.setId(id.toString());
		entetes.setDestination(destination);
		BlockingQueue<JsonNode> messages = new ArrayBlockingQueue<>(64);
		session.subscribe(entetes, new StompFrameHandler() {
			public Type getPayloadType(StompHeaders h) { return Map.class; }
			public void handleFrame(StompHeaders h, Object corps) { messages.add(json.valueToTree(corps)); }
		});
		await().atMost(Duration.ofSeconds(3)).until(() -> abonnements.estInstalle(id, destination));
		return new Abonnement(secret, destination, ecran, id, messages);
	}

	JsonNode capturer(Abonnement abonnement) throws IOException {
		String operation = "retrouverEtat" + Character.toUpperCase(abonnement.ecran().charAt(0)) + abonnement.ecran().substring(1);
		return verifier(operation, 200, envoyer(HttpMethod.GET, racine() + "/etat/" + abonnement.ecran() + "?abonnementId="
				+ abonnement.id(), abonnement.secret(), null, null));
	}

	/** Prochain événement de cette cause ; il doit suivre directement le précédent et égaler la capture. */
	JsonNode evenement(Abonnement abonnement, String type) throws Exception {
		var evenement = abonnement.messages().poll(5, TimeUnit.SECONDS);
		assertThat(evenement).as("événement " + type + " sur " + abonnement.destination()).isNotNull();
		assertThat(evenement.path("type").asString()).isEqualTo(type);
		var capture = capturer(abonnement);
		if (capture.path("revision").equals(evenement.path("revision"))) {
			assertThat(capture.path("vue")).isEqualTo(evenement.path("donnees"));
		}
		return evenement.path("donnees");
	}
}
