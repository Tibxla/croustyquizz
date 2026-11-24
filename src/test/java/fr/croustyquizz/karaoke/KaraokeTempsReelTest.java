package fr.croustyquizz.karaoke;

import static fr.croustyquizz.karaoke.ConfigurationTroisJoueurs.joueursDe;
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
    @Autowired Horloge horloge;
    @Autowired SocleDeManche socle;
    @Autowired MemoireDeCalageJpa calages;
    @MockitoSpyBean PersistanceKaraokeJpa persistance;
    final WsWebSocketContainer conteneur = new WsWebSocketContainer();
    final SimpleAsyncTaskExecutor executeur = new SimpleAsyncTaskExecutor("karaoke-stomp-");
    final List<WebSocketStompClient> clients = new ArrayList<>();
    final List<StompSession> sessions = new ArrayList<>();
    final UUID soiree = UUID.randomUUID();
    final Acteur animateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree);
    final Acteur joueur = new Acteur(Acteur.Type.JOUEUR, joueursDe(soiree).getFirst(), soiree);

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
        for (UUID id : joueursDe(soiree)) { flux.add(abonner(new Acteur(Acteur.Type.JOUEUR, id, soiree), Canaux.joueur(id), "manette")); }
        Map<Abonnement, Long> revisionsInitiales = new HashMap<>();
        for (var f : flux) {
            long revision = capturer(f).path("revision").asLong();
            assertThat(revision).isPositive();
            revisionsInitiales.put(f, revision);
        }
        String racine = "/api/soirees/" + soiree + "/manches/" + manche;
        var inscription = Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1));
        var reponse = envoyer(HttpMethod.POST, racine + "/passages", joueur, inscription, null);
        KaraokeHttpTest.verifier("inscrirePassage", reponse, 201);
        UUID passage = UUID.fromString(json.readTree(reponse.getBody()).path("passageId").asString());
        for (var f : flux) {
            JsonNode evenement = f.messages().poll(3, TimeUnit.SECONDS);
            assertThat(evenement).isNotNull();
            assertThat(evenement.path("type").asString()).isEqualTo("file-attente-modifiee");
            assertThat(evenement.path("revision").asLong()).isEqualTo(revisionsInitiales.get(f) + 1);
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
            assertThat(evenement.path("revision").asLong()).isEqualTo(revisionsInitiales.get(f) + 2); assertThat(f.messages()).isEmpty();
            assertThat(capturer(f).path("vue").toString()).isEqualTo(evenement.path("donnees").toString());
        }
        flux.getFirst().session().disconnect();
        var reconnecte = abonner(animateur, Canaux.console(soiree), "console");
        var capture = capturer(reconnecte);
        assertThat(capture.path("revision").asLong()).isEqualTo(revisionsInitiales.get(flux.getFirst()) + 2);
        assertThat(capture.at("/vue/karaoke/limitePassagesEnAttente").asLong()).isEqualTo(2);
        assertThat(reconnecte.messages()).isEmpty();
    }

    @Test void demarrerUneMancheDansUneAutreSoireeNeChangePasLaRevisionDeLaManette() throws Exception {
        var manette = abonner(joueur, Canaux.joueur(joueur.identifiant()), "manette");
        var revision = capturer(manette).path("revision");

        UUID autreSoiree = UUID.randomUUID();
        var autreAnimateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), autreSoiree);
        UUID manche = service.creer(autreSoiree, Map.of());
        assertThat(service.demarrer(manche, new EnvoiDeCommande(autreAnimateur, "/demarrer", UUID.randomUUID(), Map.of())))
                .isInstanceOf(Accepte.class);

        assertThat(capturer(manette).path("revision")).isEqualTo(revision);
    }

    @Test
    @org.springframework.test.annotation.DirtiesContext(methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.AFTER_METHOD)
    void leCyclePublieUneSeuleFinApresRestaurationEtReserveLaLectureEtLeCalageASesDestinations() throws Exception {
        UUID manche = service.creer(soiree, Map.of());
        service.demarrer(manche, new EnvoiDeCommande(animateur, "/demarrer", UUID.randomUUID(), Map.of()));
        String racine = "/api/soirees/" + soiree + "/manches/" + manche;
        var ecran = new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), soiree);
        var console = abonner(animateur, Canaux.console(soiree), "console");
        var salle = abonner(ecran, Canaux.salle(soiree), "salle");
        var manette = abonner(joueur, Canaux.joueur(joueur.identifiant()), "manette");
        var flux = List.of(console, salle, manette);

        var inscription = envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null);
        KaraokeHttpTest.verifier("inscrirePassage", inscription, 201);
        UUID passage = UUID.fromString(json.readTree(inscription.getBody()).path("passageId").asString());
        for (var abonne : flux) { lireEvenement(abonne, "file-attente-modifiee"); }
        String route = racine + "/passages/" + passage;
        var appel = envoyer(HttpMethod.POST, route + "/appel", animateur, Map.of("numeroModification", 0), UUID.randomUUID());
        KaraokeHttpTest.verifier("appelerPassage", appel, 200);
        String etape = json.readTree(appel.getBody()).path("etapeId").asString();
        for (var abonne : flux) { lireEvenement(abonne, "passage-appele"); }
        assertThat(capturer(manette).at("/vue/karaoke/peutConfirmerPresence").asBoolean()).isTrue();
        var captureAppel = capturer(salle);
        ((HorlogeFactice) horloge).avancer(Duration.ofSeconds(31));
        var apresEcheance = capturer(salle);
        assertThat(apresEcheance.path("revision")).isEqualTo(captureAppel.path("revision"));
        assertThat(apresEcheance.path("vue")).isEqualTo(captureAppel.path("vue"));

        UUID lecteur = UUID.randomUUID();
        var designation = Map.of("lecteurId", lecteur);
        KaraokeHttpTest.verifier("designerLecteurChanson",
                envoyer(HttpMethod.PUT, racine + "/lecteur", ecran, designation, UUID.randomUUID()), 200);
        lireEvenement(salle, "lecture-chanson-modifiee");
        lireEvenement(console, "lecture-chanson-modifiee");
        KaraokeHttpTest.verifier("designerLecteurChanson",
                envoyer(HttpMethod.PUT, racine + "/lecteur", ecran, designation, UUID.randomUUID()), 200);

        String corps = """
                {"lecteurId":"%s","etapeId":"%s","numeroModification":1,"source":"FICHIER_LOCAL",
                 "fichier":{"nom":"Chanson-temps-reel.mp3","tailleOctets":120,"dureeMs":180000},"motifParolesSeules":null}
                """.formatted(lecteur, etape);
        var declaration = envoyer(HttpMethod.POST, route + "/lecture", ecran, corps, UUID.randomUUID());
        KaraokeHttpTest.verifier("declarerLectureChanson", declaration, 200);
        String lecture = json.readTree(declaration.getBody()).path("lectureId").asString();
        assertThat(lireEvenement(salle, "lecture-chanson-modifiee").at("/karaoke/paroles").isNull()).isTrue();
        assertThat(lireEvenement(console, "lecture-chanson-modifiee").at("/karaoke/fichierLocal/nom").asString()).isEqualTo("Chanson-temps-reel.mp3");

        var presence = Map.of("envoiId", UUID.randomUUID(), "etapeId", etape, "numeroModification", 1);
        KaraokeHttpTest.verifier("confirmerPresencePassage", envoyer(HttpMethod.POST, route + "/presence", joueur, presence, null), 200);
        var vueSalle = lireEvenement(salle, "passage-demarre");
        assertThat(vueSalle.at("/karaoke/paroles/lignes").size()).isPositive();
        assertThat(vueSalle.toString()).doesNotContain("Chanson-temps-reel.mp3", "versionSourceId");
        var vueConsole = lireEvenement(console, "passage-demarre");
        assertThat(vueConsole.at("/karaoke/peutCaler").asBoolean()).isTrue();
        assertThat(vueConsole.toString()).doesNotContain("Une ligne", "versionSourceId");
        var vueManette = lireEvenement(manette, "passage-demarre");
        assertThat(vueManette.toString()).doesNotContain("lectureId", "Une ligne", "Chanson-temps-reel.mp3");

        var position = Map.of("etapeId", etape, "lectureId", lecture);
        KaraokeHttpTest.verifier("marquerDebutParoles",
                envoyer(HttpMethod.POST, route + "/calage/debut", animateur, position, UUID.randomUUID()), 200);
        lireEvenement(salle, "calage-chanson-modifie");
        lireEvenement(console, "calage-chanson-modifie");

        var correction = Map.of("etapeId", etape, "lectureId", lecture, "numeroModificationCalage", 0, "sens", "RETARDER");
        UUID idCorrection = UUID.randomUUID();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(socle.transactions());
        String chansonId = IdentifiantsChanson.encoder(1);
        String nomFichier = "Chanson-temps-reel.mp3";
        doThrow(new DataAccessResourceFailureException("Panne factice")).when(persistance).enregistrer(eq(manche), any());
        try {
            var echec = envoyer(HttpMethod.POST, route + "/calage/correction", animateur, correction, idCorrection);
            KaraokeHttpTest.verifier("corrigerCalage", echec, 503);
            assertThat(json.readTree(echec.getBody()).path("code").asString()).isEqualTo("SERVICE_INDISPONIBLE");
        } finally { doCallRealMethod().when(persistance).enregistrer(eq(manche), any()); }
        transaction.executeWithoutResult(s -> assertThat(calages.lire(chansonId, 1, nomFichier, 120)).isZero());
        KaraokeHttpTest.verifier("corrigerCalage",
                envoyer(HttpMethod.POST, route + "/calage/correction", animateur, correction, idCorrection), 200);
        assertThat(lireEvenement(salle, "calage-chanson-modifie").at("/karaoke/lecture/decalageMs").asLong()).isEqualTo(100);
        lireEvenement(console, "calage-chanson-modifie");
        transaction.executeWithoutResult(s -> assertThat(calages.lire(chansonId, 1, nomFichier, 120)).isEqualTo(100));

        var erreur = Map.of("lecteurId", lecteur, "etapeId", etape, "lectureId", lecture);
        UUID idErreur = UUID.randomUUID();
        var avant = service.instantane(soiree, manche);
        doThrow(new DataAccessResourceFailureException("Panne factice")).when(persistance).enregistrer(eq(manche), any());
        try {
            KaraokeHttpTest.verifier("signalerErreurLectureChanson",
                    envoyer(HttpMethod.POST, route + "/erreur-lecture", ecran, erreur, idErreur), 503);
        } finally { doCallRealMethod().when(persistance).enregistrer(eq(manche), any()); }
        assertThat(socle.depot().charger(manche).getEtat()).isEqualTo(EtatManche.EN_COURS);
        assertThat(service.instantane(soiree, manche)).isEqualTo(avant);
        KaraokeHttpTest.verifier("signalerErreurLectureChanson",
                envoyer(HttpMethod.POST, route + "/erreur-lecture", ecran, erreur, idErreur), 200);
        assertThat(lireEvenement(salle, "lecture-chanson-modifiee").at("/karaoke/courant/etat").asString()).isEqualTo("EN_PAUSE");
        var consoleEnErreur = lireEvenement(console, "lecture-chanson-modifiee");
        assertThat(consoleEnErreur.at("/karaoke/peutReprendreEnParolesSeules").asBoolean()).isTrue();
        assertThat(consoleEnErreur.at("/karaoke/peutReprendre").asBoolean()).isFalse();
        lireEvenement(manette, "lecture-chanson-modifiee");

        KaraokeHttpTest.verifier("reprendreEnParolesSeules",
                envoyer(HttpMethod.POST, route + "/paroles-seules", animateur, position, UUID.randomUUID()), 200);
        for (var abonne : flux) { lireEvenement(abonne, "lecture-chanson-modifiee"); }
        socle.registre().retirer(manche);
        var restaure = (InstantaneKaraoke) service.instantane(soiree, manche);
        assertThat(restaure.lectures().get(passage).motifParolesSeules()).isEqualTo(MotifParolesSeules.ERREUR_LECTURE);
        Map<Abonnement, Long> revisionsAvantFin = new HashMap<>();
        for (var abonne : flux) {
            revisionsAvantFin.put(abonne, capturer(abonne).path("revision").asLong());
        }
        ((HorlogeFactice) horloge).avancer(Duration.ofSeconds(180));
        for (var abonne : flux) {
            var terminee = lireEvenement(abonne, "passage-termine");
            assertThat(terminee.at("/karaoke/courant/etat").asString()).isEqualTo("TERMINE");
        }
        var signalTardif = envoyer(HttpMethod.POST, route + "/fin-lecture", ecran,
                Map.of("lecteurId", lecteur, "etapeId", etape, "lectureId", lecture, "positionMs", 180_000), UUID.randomUUID());
        KaraokeHttpTest.verifier("signalerFinLectureChanson", signalTardif, 409);
        assertThat(json.readTree(signalTardif.getBody()).path("code").asString()).isEqualTo("COMMANDE_PERIMEE");
        assertThat(capturer(salle).at("/vue/karaoke/lecture").isNull()).isTrue();
        for (var abonne : flux) {
            assertThat(capturer(abonne).path("revision").asLong()).isEqualTo(revisionsAvantFin.get(abonne) + 1);
        }
    }

    @Test void laNoteVaAuVotantEtLaClotureAToutesLesManettes() throws Exception {
        UUID manche = service.creer(soiree, Map.of());
        service.demarrer(manche, new EnvoiDeCommande(animateur, "/demarrer", UUID.randomUUID(), Map.of()));
        String racine = "/api/soirees/" + soiree + "/manches/" + manche;
        var inscription = envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null);
        KaraokeHttpTest.verifier("inscrirePassage", inscription, 201);
        String route = racine + "/passages/" + json.readTree(inscription.getBody()).path("passageId").asString();
        var appel = envoyer(HttpMethod.POST, route + "/appel", animateur, Map.of("numeroModification", 0), UUID.randomUUID());
        KaraokeHttpTest.verifier("appelerPassage", appel, 200);
        var ecran = new Acteur(Acteur.Type.ECRAN, UUID.randomUUID(), soiree);
        var votant = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.joueursDe(soiree).get(1), soiree);
        var autre = new Acteur(Acteur.Type.JOUEUR, ConfigurationTroisJoueurs.joueursDe(soiree).get(2), soiree);
        var salle = abonner(ecran, Canaux.salle(soiree), "salle");
        var console = abonner(animateur, Canaux.console(soiree), "console");
        var interprete = abonner(joueur, Canaux.joueur(joueur.identifiant()), "manette");
        var manette = abonner(votant, Canaux.joueur(votant.identifiant()), "manette");
        var autreManette = abonner(autre, Canaux.joueur(autre.identifiant()), "manette");
        var flux = List.of(salle, console, interprete, manette, autreManette);
        KaraokeHttpTest.verifier("confirmerPresencePassage", envoyer(HttpMethod.POST, route + "/presence", joueur,
                Map.of("envoiId", UUID.randomUUID(), "etapeId", json.readTree(appel.getBody()).path("etapeId").asString(),
                        "numeroModification", 1), null), 200);
        for (var abonne : flux) { lireEvenement(abonne, "passage-demarre"); }
        ((HorlogeFactice) horloge).avancer(Duration.ofSeconds(180));
        for (var abonne : flux) { lireEvenement(abonne, "passage-termine"); }

        var corps = Map.of("envoiId", UUID.randomUUID(), "numeroModification", 3, "note", 4);
        KaraokeHttpTest.verifier("voterPassage", envoyer(HttpMethod.POST, route + "/vote", votant, corps, null), 200);
        assertThat(lireEvenement(salle, "vote-enregistre").at("/karaoke/votes").isMissingNode()).isTrue();
        assertThat(lireEvenement(console, "vote-enregistre").at("/karaoke/votes/0/nombreVotes").asInt()).isEqualTo(1);
        assertThat(lireEvenement(manette, "vote-enregistre").at("/karaoke/votes/0/maNote").asInt()).isEqualTo(4);
        socle.registre().retirer(manche);
        assertThat(service.instantane(soiree, manche)).isInstanceOf(InstantaneKaraoke.class);
        ((HorlogeFactice) horloge).avancer(Duration.ofSeconds(30));
        for (var abonne : flux) { lireEvenement(abonne, "vote-clos"); }
        assertThat(capturer(autreManette).at("/vue/karaoke/votes/0/maNote").isNull()).isTrue();
    }

    private JsonNode lireEvenement(Abonnement abonne, String cause) throws Exception {
        var evenement = abonne.messages().poll(3, TimeUnit.SECONDS);
        assertThat(evenement).isNotNull();
        assertThat(evenement.path("soireeId").asString()).isEqualTo(soiree.toString());
        assertThat(evenement.path("type").asString()).isEqualTo(cause);
        var capture = capturer(abonne);
        assertThat(capture.path("revision")).isEqualTo(evenement.path("revision"));
        assertThat(capture.path("vue").toString()).isEqualTo(evenement.path("donnees").toString());
        return evenement.path("donnees");
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
