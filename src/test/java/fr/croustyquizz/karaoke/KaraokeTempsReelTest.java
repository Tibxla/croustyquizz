package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.*;
import fr.croustyquizz.tempsreel.*;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.*;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP → PostgreSQL → commit → STOMP et capture de reconnexion, à trois Joueurs. Exige Docker. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, ConfigurationTroisJoueurs.class})
class KaraokeTempsReelTest {
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired RegistreAbonnements abonnements;
    @Autowired ServiceKaraoke service;
    @MockitoSpyBean PersistanceKaraokeJpa persistance;
    final WsWebSocketContainer conteneur = new WsWebSocketContainer();
    final SimpleAsyncTaskExecutor executeur = new SimpleAsyncTaskExecutor("karaoke-stomp-");
    final List<WebSocketStompClient> clients = new ArrayList<>();
    final List<StompSession> sessions = new ArrayList<>();
    final UUID soiree = UUID.randomUUID();
    final Acteur animateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree);
    final Acteur joueur = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.JOUEURS.getFirst(), soiree);

    @AfterEach void fermer() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        clients.forEach(WebSocketStompClient::stop); executeur.close(); conteneur.destroy();
    }

    @Test void seulesLesMutationsValideesPublientLesVuesCompletesEtLaReconnexionLesRetrouve() throws Exception {
        var creation = envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree + "/manches", animateur,
                Map.of("mode", "KARAOKE", "reglages", Map.of()), null);
        KaraokeHttpTest.verifierPrototype("creerMancheDeTest", creation, 201);
        UUID manche = UUID.fromString(json.readTree(creation.getBody()).path("mancheId").asString());
        var demarrage = envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree + "/manches/" + manche + "/demarrer", animateur, null, UUID.randomUUID());
        KaraokeHttpTest.verifierPrototype("demarrerMancheDeTest", demarrage, 200);
        List<Abonnement> flux = new ArrayList<>();
        flux.add(abonner(animateur, Canaux.console(soiree), "console"));
        flux.add(abonner(new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), soiree), Canaux.salle(soiree), "salle"));
        for (UUID id : ConfigurationTroisJoueurs.JOUEURS) { flux.add(abonner(new Acteur(Acteur.Type.JOUEUR, id, soiree), Canaux.joueur(id), "manette")); }
        for (var f : flux) { assertThat(capturer(f).path("revision").asLong()).isEqualTo(1); }
        String racine = "/api/soirees/" + soiree + "/manches/" + manche;
        var inscription = Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1));
        var reponse = envoyer(HttpMethod.POST, racine + "/passages", joueur, inscription, null);
        KaraokeHttpTest.verifier("inscrirePassage", reponse, 201);
        UUID passage = UUID.fromString(json.readTree(reponse.getBody()).path("passageId").asString());
        for (var f : flux) {
            JsonNode evenement = f.messages().poll(3, TimeUnit.SECONDS);
            assertThat(evenement).isNotNull();
            assertThat(evenement.path("type").asString()).isEqualTo("file-attente-modifiee");
            assertThat(evenement.path("revision").asLong()).isEqualTo(2);
            assertThat(evenement.at("/donnees/karaoke/nombreEnAttente").asLong()).isEqualTo(1);
            assertThat(evenement.toString()).doesNotContain("idLrclib", "lignesParoles", "Une ligne");
            assertThat(capturer(f).path("vue").toString()).isEqualTo(evenement.path("donnees").toString());
        }
        // Le 201 rejoué, un refus et une limite inchangée n'envoient aucun événement.
        KaraokeHttpTest.verifier("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", joueur, inscription, null), 201);
        KaraokeHttpTest.verifier("inscrirePassage", envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(2)), null), 409);
        KaraokeHttpTest.verifier("reglerLimitePassages", envoyer(HttpMethod.PUT, racine + "/file-attente/limite", animateur,
                Map.of("limitePassagesEnAttente", 1), UUID.randomUUID()), 200);
        Object avant = service.instantane(soiree, manche);
        doThrow(new DataAccessResourceFailureException("Panne factice")).when(persistance).enregistrer(eq(manche), any());
        try {
            KaraokeHttpTest.verifier("retirerPassage", envoyer(HttpMethod.POST, racine + "/passages/" + passage + "/retrait", animateur,
                    Map.of("numeroModification", 0), UUID.randomUUID()), 503);
        } finally { doCallRealMethod().when(persistance).enregistrer(eq(manche), any()); }
        assertThat(service.instantane(soiree, manche)).isEqualTo(avant);
        // Cette mutation sert aussi de barrière : elle doit être le seul événement suivant.
        KaraokeHttpTest.verifier("reglerLimitePassages", envoyer(HttpMethod.PUT, racine + "/file-attente/limite", animateur,
                Map.of("limitePassagesEnAttente", 2), UUID.randomUUID()), 200);
        for (var f : flux) {
            var evenement = f.messages().poll(3, TimeUnit.SECONDS);
            assertThat(evenement).isNotNull(); assertThat(evenement.path("type").asString()).isEqualTo("limite-passages-modifiee");
            assertThat(evenement.path("revision").asLong()).isEqualTo(3); assertThat(f.messages()).isEmpty();
            assertThat(capturer(f).path("vue").toString()).isEqualTo(evenement.path("donnees").toString());
        }
        flux.getFirst().session().disconnect();
        var reconnecte = abonner(animateur, Canaux.console(soiree), "console");
        var capture = capturer(reconnecte);
        assertThat(capture.path("revision").asLong()).isEqualTo(3);
        assertThat(capture.at("/vue/karaoke/limitePassagesEnAttente").asLong()).isEqualTo(2);
        assertThat(reconnecte.messages()).isEmpty();
    }

    record Abonnement(Acteur acteur, String destination, String ecran, UUID id, StompSession session, BlockingQueue<JsonNode> messages) { }
    Abonnement abonner(Acteur acteur, String destination, String ecran) throws Exception {
        var websocket = new StandardWebSocketClient(conteneur); websocket.setTaskExecutor(executeur);
        var client = new WebSocketStompClient(websocket); clients.add(client);
        client.setMessageConverter(new JacksonJsonMessageConverter());
        var connexion = new StompHeaders(); connexion.set("Authorization", jeton(acteur));
        var session = client.connectAsync("ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), connexion, new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
        sessions.add(session);
        UUID id = UUID.randomUUID(); var entetes = new StompHeaders(); entetes.setId(id.toString()); entetes.setDestination(destination);
        BlockingQueue<JsonNode> messages = new ArrayBlockingQueue<>(32);
        session.subscribe(entetes, new StompFrameHandler() {
            public Type getPayloadType(StompHeaders h) { return Map.class; }
            public void handleFrame(StompHeaders h, Object corps) { messages.add(json.valueToTree(corps)); }
        });
        await().atMost(Duration.ofSeconds(3)).until(() -> abonnements.estInstalle(id, destination));
        return new Abonnement(acteur, destination, ecran, id, session, messages);
    }
    JsonNode capturer(Abonnement f) throws Exception {
        var reponse = envoyer(HttpMethod.GET, "/api/soirees/" + soiree + "/etat/" + f.ecran() + "?abonnementId=" + f.id(), f.acteur(), null, null);
        KaraokeHttpTest.verifier("retrouverEtat" + Character.toUpperCase(f.ecran().charAt(0)) + f.ecran().substring(1), reponse, 200);
        return json.readTree(reponse.getBody());
    }
    ResponseEntity<String> envoyer(HttpMethod methode, String route, Acteur acteur, Object corps, UUID id) {
        var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route).header("Authorization", jeton(acteur));
        if (id != null) { requete.header("Idempotency-Key", id.toString()); }
        if (corps != null) { requete.contentType(MediaType.APPLICATION_JSON).body(corps); }
        return requete.retrieve().onStatus(s -> s.isError(), (q, r) -> { }).toEntity(String.class);
    }
    static String jeton(Acteur acteur) { return "Bearer demo:" + acteur.type() + ":" + acteur.soireeId() + ":" + acteur.identifiant(); }
}
