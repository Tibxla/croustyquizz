package fr.croustyquizz.freestyle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.HorlogeFactice;
import fr.croustyquizz.manche.SocleDeManche;
import fr.croustyquizz.tempsreel.Canaux;
import fr.croustyquizz.tempsreel.RegistreAbonnements;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, FreestyleTempsReelTest.Temps.class})
class FreestyleTempsReelTest {
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired HorlogeFactice horloge;
    @Autowired SocleDeManche socle;
    @Autowired ServiceFreestyle service;
    @Autowired RegistreAbonnements abonnements;

    @Test void uneSoireeEnregistreeJoueDeuxPassagesEtPublieLeurPalmaresSansPoints() throws Exception {
        var soiree = appeler(POST, "/api/soirees", null,
                Map.of("titre", "Freestyle du vendredi", "format", "SOLO", "pilotage", "MANUEL"), "creerSoiree", 201);
        String id = soiree.path("id").asString();
        String racine = "/api/soirees/" + id;
        String animateur = "demo:ANIMATEUR:" + id + ":" + UUID.randomUUID();
        String lecteur = appeler(POST, racine + "/acces-ecran", animateur, null, "creerAccesEcran", 200).path("cleEcran").asString();
        String code = appeler(POST, racine + "/ouverture", animateur, null, "ouvrirSoiree", 200).path("codeAcces").asString();
        String lea = rejoindre(code, "Léa");
        String tom = rejoindre(code, "Tom");
        String catalogue = racine + "/freestyle/instrumentales";
        UUID fiche = UUID.randomUUID();
        var instrumentale = Map.of("instrumentaleId", fiche, "titre", "À la lueur", "tempoBpm", 120, "departBeatMs", 250);
        var doublon = Map.of("instrumentales", List.of(instrumentale, instrumentale));
        assertThat(appeler(PUT, catalogue, lecteur, doublon, "declarerInstrumentales", 422).path("code").asString())
                .isEqualTo("DONNEES_INVALIDES");
        var declaration = Map.of("instrumentales", List.of(instrumentale));
        UUID cle = UUID.randomUUID();
        var recu = appeler(PUT, catalogue, lecteur, declaration, "declarerInstrumentales", 200, cle);
        assertThat(appeler(PUT, catalogue, lecteur, declaration, "declarerInstrumentales", 200, cle)).isEqualTo(recu);
        assertThat(appeler(PUT, catalogue, lecteur, Map.of("instrumentales", List.of()), "declarerInstrumentales", 409, cle)
                .path("code").asString()).isEqualTo("ENVOI_REUTILISE");
        assertThat(appeler(GET, catalogue, animateur, null, "consulterInstrumentales", 200).at("/instrumentales/0/titre").asString())
                .isEqualTo("À la lueur");
        var reglages = Map.of("theme", "VILLE", "schemaDeRimes", "ABAB", "difficulte", "PREMIER",
                "nombreMesures", 8, "instrumentaleIds", List.of(fiche));
        String manche = appeler(POST, racine + "/manches", animateur, Map.of("mode", "FREESTYLE", "reglages", reglages),
                "ajouterManche", 201).path("id").asString();
        assertThat(manche).isNotBlank();

        // Le lancement de la Soirée relève de #12. La Manche démarre par la route du profil recette.
        jdbc.update("update soiree set etat = 'EN_COURS', debutee_le = now() where id = ?::uuid", id);
        appeler(POST, "/api/test/soirees/" + id + "/manches/" + manche + "/demarrer", animateur, null, "demarrerMancheDeTest", 200);
        String routeManche = racine + "/manches/" + manche;
        var websocket = new StandardWebSocketClient();
        var executeur = new SimpleAsyncTaskExecutor("freestyle-stomp-");
        websocket.setTaskExecutor(executeur);
        var stomp = new WebSocketStompClient(websocket);
        stomp.setMessageConverter(new JacksonJsonMessageConverter());
        StompSession session = null;
        try {
            var connexion = new StompHeaders();
            connexion.set("Authorization", "Bearer " + lecteur);
            session = stomp.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connexion,
                    new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
            var messages = new LinkedBlockingQueue<JsonNode>();
            String destination = Canaux.salle(UUID.fromString(id));
            var abonnement = new StompHeaders();
            UUID abonnementId = UUID.randomUUID();
            abonnement.setId(abonnementId.toString());
            abonnement.setDestination(destination);
            session.subscribe(abonnement, new StompFrameHandler() {
                public Type getPayloadType(StompHeaders entetes) { return Map.class; }
                public void handleFrame(StompHeaders entetes, Object corps) { messages.add(json.valueToTree(corps)); }
            });
            await().atMost(Duration.ofSeconds(3)).until(() -> abonnements.estInstalle(abonnementId, destination));
            String capture = racine + "/etat/salle?abonnementId=" + abonnementId;
            var avant = appeler(GET, capture, lecteur, null, "retrouverEtatSalle", 200);
            jouer(routeManche, manche, animateur, lea, tom, fiche, 2, messages);
            jouer(routeManche, manche, animateur, tom, lea, null, 5, messages);
            appeler(POST, routeManche + "/commandes", animateur, Map.of("action", "TERMINER"), "piloterManche", 200);
            var fin = evenement(messages, "manche-terminee");
            var palmares = fin.at("/donnees/palmaresFreestyle");
            assertThat(palmares.size()).isEqualTo(2);
            assertThat(palmares.at("/0/interprete/pseudo").asString()).isEqualTo("Tom");
            assertThat(palmares.at("/0/moyenneDixiemes").asInt()).isEqualTo(50);
            assertThat(palmares.at("/1/interprete/pseudo").asString()).isEqualTo("Léa");
            assertThat(fin.at("/donnees/classement")).isEqualTo(avant.at("/vue/classement"));
            var retrouvee = appeler(GET, capture, lecteur, null, "retrouverEtatSalle", 200);
            assertThat(retrouvee.path("revision")).isEqualTo(fin.path("revision"));
            assertThat(retrouvee.path("vue")).isEqualTo(fin.path("donnees"));
            var console = appeler(GET, racine + "/etat/console", animateur, null, "retrouverEtatConsole", 200);
            assertThat(console.at("/vue/palmaresFreestyle")).isEqualTo(palmares);
        } finally {
            if (session != null && session.isConnected()) { session.disconnect(); }
            stomp.stop();
            executeur.close();
        }
    }

    @Test void unTrioRappeSesCoupletsDansLOrdreTireEtLePublicDesigneLeMeilleurInterprete() throws Exception {
        var soiree = appeler(POST, "/api/soirees", null,
                Map.of("titre", "Freestyle à plusieurs", "format", "SOLO", "pilotage", "MANUEL"), "creerSoiree", 201);
        String id = soiree.path("id").asString();
        String racine = "/api/soirees/" + id;
        String animateur = "demo:ANIMATEUR:" + id + ":" + UUID.randomUUID();
        String lecteur = appeler(POST, racine + "/acces-ecran", animateur, null, "creerAccesEcran", 200).path("cleEcran").asString();
        String code = appeler(POST, racine + "/ouverture", animateur, null, "ouvrirSoiree", 200).path("codeAcces").asString();
        var ines = acces(code, "Inès");
        var mael = acces(code, "Maël");
        var zoe = acces(code, "Zoé");
        var noe = acces(code, "Noé");
        UUID fiche = UUID.randomUUID();
        appeler(PUT, racine + "/freestyle/instrumentales", lecteur, Map.of("instrumentales", List.of(Map.of(
                "instrumentaleId", fiche, "titre", "Pavés mouillés", "tempoBpm", 120, "departBeatMs", 250))),
                "declarerInstrumentales", 200);
        var reglages = new HashMap<String, Object>(Map.of("theme", "VILLE", "schemaDeRimes", "ABAB", "difficulte", "TOUS",
                "nombreMesures", 8, "mesuresParCouplet", 3, "instrumentaleIds", List.of(fiche)));
        appeler(POST, racine + "/manches", animateur, Map.of("mode", "FREESTYLE", "reglages", reglages), "ajouterManche", 400);
        reglages.put("mesuresParCouplet", 2);
        var creee = appeler(POST, racine + "/manches", animateur, Map.of("mode", "FREESTYLE", "reglages", reglages),
                "ajouterManche", 201);
        assertThat(creee.at("/reglages/mesuresParCouplet").asInt()).isEqualTo(2);
        String manche = creee.path("id").asString();
        jdbc.update("update soiree set etat = 'EN_COURS', debutee_le = now() where id = ?::uuid", id);
        appeler(POST, "/api/test/soirees/" + id + "/manches/" + manche + "/demarrer", animateur, null, "demarrerMancheDeTest", 200);
        String routeManche = racine + "/manches/" + manche;
        var websocket = new StandardWebSocketClient();
        var executeur = new SimpleAsyncTaskExecutor("freestyle-trio-");
        websocket.setTaskExecutor(executeur);
        var stomp = new WebSocketStompClient(websocket);
        stomp.setMessageConverter(new JacksonJsonMessageConverter());
        StompSession session = null;
        try {
            var connexion = new StompHeaders();
            connexion.set("Authorization", "Bearer " + lecteur);
            session = stomp.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connexion,
                    new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
            var messages = new LinkedBlockingQueue<JsonNode>();
            String destination = Canaux.salle(UUID.fromString(id));
            var abonnement = new StompHeaders();
            UUID abonnementId = UUID.randomUUID();
            abonnement.setId(abonnementId.toString());
            abonnement.setDestination(destination);
            session.subscribe(abonnement, new StompFrameHandler() {
                public Type getPayloadType(StompHeaders entetes) { return Map.class; }
                public void handleFrame(StompHeaders entetes, Object corps) { messages.add(json.valueToTree(corps)); }
            });
            await().atMost(Duration.ofSeconds(3)).until(() -> abonnements.estInstalle(abonnementId, destination));

            var soi = new HashMap<String, Object>(Map.of("envoiId", UUID.randomUUID(), "invitesIds", List.of(ines.id())));
            soi.put("instrumentaleId", null);
            assertThat(appeler(POST, routeManche + "/passages", ines.jeton(), soi, "inscrirePassage", 422).path("code").asString())
                    .isEqualTo("DONNEES_INVALIDES");
            var inscription = new HashMap<String, Object>(Map.of("envoiId", UUID.randomUUID(), "instrumentaleId", fiche,
                    "invitesIds", List.of(mael.id(), zoe.id())));
            String passage = routeManche + "/passages/"
                    + appeler(POST, routeManche + "/passages", ines.jeton(), inscription, "inscrirePassage", 201).path("passageId").asString();
            evenement(messages, "file-attente-modifiee");
            var invitee = appeler(GET, racine + "/etat/manette", zoe.jeton(), null, "retrouverEtatManette", 200);
            assertThat(invitee.at("/vue/freestyle/invitationsRecues/0/instrumentale/titre").asString()).isEqualTo("Pavés mouillés");
            for (var reponse : List.of(Map.entry(mael, 0), Map.entry(zoe, 1))) {
                appeler(POST, passage + "/invitation", reponse.getKey().jeton(), Map.of("envoiId", UUID.randomUUID(),
                        "numeroModification", reponse.getValue(), "reponse", "ACCEPTER"), "repondreInvitation", 200);
                evenement(messages, "file-attente-modifiee");
            }
            var console = appeler(GET, racine + "/etat/console", animateur, null, "retrouverEtatConsole", 200);
            assertThat(console.at("/vue/freestyle/passagesEnAttente/0/interpretes").size()).isEqualTo(3);
            var appel = appeler(POST, passage + "/appel", animateur, Map.of("numeroModification", 2), "appelerPassage", 200);
            evenement(messages, "passage-appele");
            appeler(POST, passage + "/presence", zoe.jeton(), Map.of("envoiId", UUID.randomUUID(), "numeroModification", 3,
                    "etapeId", appel.path("etapeId").asString()), "confirmerPresencePassage", 200);
            var ordre = evenement(messages, "passage-demarre").at("/donnees/freestyle/courant/couplets/ordre");
            assertThat(ordre.size()).isEqualTo(3);
            var suivant = Map.of(ines.id(), ines, mael.id(), mael, zoe.id(), zoe).get(UUID.fromString(ordre.at("/1/joueurId").asString()));

            horloge.avancer(Duration.ofMillis(2_250));
            var annonce = evenement(messages, "couplet-annonce");
            assertThat(annonce.at("/donnees/freestyle/courant/couplets/suivantAnnonce").asBoolean()).isTrue();
            assertThat(annonce.at("/donnees/freestyle/courant/couplets/ordre")).isEqualTo(ordre);
            var salle = appeler(GET, racine + "/etat/salle", lecteur, null, "retrouverEtatSalle", 200);
            assertThat(salle.at("/vue/freestyle/courant/couplets")).isEqualTo(annonce.at("/donnees/freestyle/courant/couplets"));
            assertThat(appeler(GET, racine + "/etat/manette", suivant.jeton(), null, "retrouverEtatManette", 200)
                    .at("/vue/freestyle/monCoupletAnnonce").asBoolean()).isTrue();
            assertThat(appeler(GET, racine + "/etat/manette", noe.jeton(), null, "retrouverEtatManette", 200)
                    .at("/vue/freestyle/monCoupletAnnonce").asBoolean()).isFalse();
            horloge.avancer(Duration.ofMillis(2_000));
            assertThat(evenement(messages, "couplet-commence").at("/donnees/freestyle/courant/couplets/courant/numero").asInt())
                    .isEqualTo(2);
            horloge.avancer(Duration.ofMillis(12_000));
            for (var type : List.of("couplet-annonce", "couplet-commence", "couplet-annonce", "couplet-commence", "passage-termine")) {
                evenement(messages, type);
            }

            assertThat(appeler(POST, passage + "/vote", noe.jeton(), Map.of("envoiId", UUID.randomUUID(), "numeroModification", 5,
                    "note", 4), "voterPassage", 422).path("code").asString()).isEqualTo("DONNEES_INVALIDES");
            String designation = passage + "/meilleur-interprete";
            assertThat(appeler(POST, designation, mael.jeton(), Map.of("envoiId", UUID.randomUUID(), "numeroModification", 5,
                    "interpreteId", zoe.id()), "designerMeilleurInterprete", 409).path("code").asString()).isEqualTo("VOTE_INTERDIT");
            assertThat(appeler(POST, designation, noe.jeton(), Map.of("envoiId", UUID.randomUUID(), "numeroModification", 5,
                    "interpreteId", noe.id()), "designerMeilleurInterprete", 422).path("code").asString()).isEqualTo("DONNEES_INVALIDES");
            var choix = Map.of("envoiId", UUID.randomUUID(), "numeroModification", 5, "interpreteId", zoe.id());
            var recu = appeler(POST, designation, noe.jeton(), choix, "designerMeilleurInterprete", 200);
            assertThat(appeler(POST, designation, noe.jeton(), choix, "designerMeilleurInterprete", 200)).isEqualTo(recu);
            evenement(messages, "vote-enregistre");
            assertThat(appeler(GET, racine + "/etat/manette", noe.jeton(), null, "retrouverEtatManette", 200)
                    .at("/vue/freestyle/votes/0/monChoix").asString()).isEqualTo(zoe.id().toString());
            horloge.avancer(Duration.ofSeconds(30));
            var voix = evenement(messages, "vote-clos").at("/donnees/freestyle/votes/0/voix");
            assertThat(voix.findValuesAsString("pseudo")).containsExactly("Inès", "Maël", "Zoé");
            assertThat(voix.at("/2/nombreVoix").asInt()).isEqualTo(1);

            appeler(POST, routeManche + "/commandes", animateur, Map.of("action", "TERMINER"), "piloterManche", 200);
            var palmares = evenement(messages, "manche-terminee").at("/donnees/palmaresFreestyle");
            assertThat(palmares.size()).isEqualTo(1);
            assertThat(palmares.at("/0/interprete/pseudo").asString()).isEqualTo("Zoé");
            assertThat(palmares.at("/0/nombreVictoires").asInt()).isEqualTo(1);
            var lignes = appeler(GET, racine + "/etat/console", animateur, null, "retrouverEtatConsole", 200).at("/vue/palmaresFreestyle");
            assertThat(lignes.findValuesAsString("pseudo")).containsExactly("Zoé", "Inès", "Maël");
            assertThat(lignes.at("/1/rang").isNull()).isTrue();
        } finally {
            if (session != null && session.isConnected()) { session.disconnect(); }
            stomp.stop();
            executeur.close();
        }
    }

    private record Joueur(UUID id, String jeton) { }

    private Joueur acces(String code, String pseudo) throws Exception {
        var acces = appeler(POST, "/api/soirees/rejoindre", null, Map.of("codeAcces", code, "pseudo", pseudo), "rejoindreSoiree", 201);
        return new Joueur(UUID.fromString(acces.at("/joueur/id").asString()), acces.path("jetonReconnexion").asString());
    }

    private void jouer(String racine, String manche, String animateur, String interprete, String votant,
            UUID instrumentale, int note, LinkedBlockingQueue<JsonNode> messages) throws Exception {
        var inscription = new HashMap<String, Object>();
        inscription.put("envoiId", UUID.randomUUID());
        inscription.put("instrumentaleId", instrumentale);
        var recu = appeler(POST, racine + "/passages", interprete, inscription, "inscrirePassage", 201);
        String passage = racine + "/passages/" + recu.path("passageId").asString();
        evenement(messages, "file-attente-modifiee");
        var appel = appeler(POST, passage + "/appel", animateur, Map.of("numeroModification", 0), "appelerPassage", 200);
        evenement(messages, "passage-appele");
        appeler(POST, passage + "/presence", interprete, Map.of("envoiId", UUID.randomUUID(), "numeroModification", 1,
                "etapeId", appel.path("etapeId").asString()), "confirmerPresencePassage", 200);
        var depart = evenement(messages, "passage-demarre");
        assertThat(depart.at("/donnees/freestyle/grille/mots/0/positionMs").asLong()).isEqualTo(1250);
        assertThat(depart.at("/donnees/freestyle/grille/mots").size()).isEqualTo(8);
        socle.registre().retirer(UUID.fromString(manche));
        String soiree = depart.path("soireeId").asString();
        var restaure = (InstantaneFreestyle) service.instantane(UUID.fromString(soiree), UUID.fromString(manche));
        assertThat(restaure.etat().cycle().etapeId().toString()).isEqualTo(appel.path("etapeId").asString());
        var capture = appeler(GET, "/api/soirees/" + soiree + "/etat/manette", votant, null, "retrouverEtatManette", 200);
        assertThat(capture.at("/vue/freestyle/grille/mots").size()).isEqualTo(8);
        if (instrumentale != null) {
            String etape = appel.path("etapeId").asString();
            appeler(POST, racine + "/commandes", animateur, Map.of("action", "METTRE_EN_PAUSE", "etapeId", etape),
                    "piloterManche", 200);
            var pause = evenement(messages, "manche-en-pause");
            assertThat(pause.at("/donnees/freestyle/courant/etat").asString()).isEqualTo("EN_PAUSE");
            assertThat(pause.at("/donnees/freestyle/grille/mots")).isEqualTo(depart.at("/donnees/freestyle/grille/mots"));
            horloge.avancer(Duration.ofSeconds(20));
            assertThat(messages).isEmpty();
            appeler(POST, racine + "/commandes", animateur, Map.of("action", "REPRENDRE", "etapeId", etape), "piloterManche", 200);
            evenement(messages, "manche-reprise");
        }
        horloge.avancer(Duration.ofMillis(16_250));
        assertThat(evenement(messages, "passage-termine").at("/donnees/freestyle/grille").isNull()).isTrue();
        appeler(POST, passage + "/vote", votant, Map.of("envoiId", UUID.randomUUID(), "numeroModification", 3, "note", note),
                "voterPassage", 200);
        evenement(messages, "vote-enregistre");
        horloge.avancer(Duration.ofSeconds(30));
        evenement(messages, "vote-clos");
    }

    private String rejoindre(String code, String pseudo) throws Exception {
        return appeler(POST, "/api/soirees/rejoindre", null, Map.of("codeAcces", code, "pseudo", pseudo), "rejoindreSoiree", 201)
                .path("jetonReconnexion").asString();
    }

    private JsonNode appeler(HttpMethod methode, String route, String secret, Object corps, String operation, int statut) throws Exception {
        return appeler(methode, route, secret, corps, operation, statut, UUID.randomUUID());
    }

    private JsonNode appeler(HttpMethod methode, String route, String secret, Object corps, String operation, int statut, UUID cle)
            throws Exception {
        var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route);
        if (secret != null) { requete.header("Authorization", "Bearer " + secret); }
        if (methode != GET) { requete.header("Idempotency-Key", cle.toString()).header("X-CSRF-TOKEN", "csrf_demo_000000000"); }
        if (corps != null) { requete.contentType(MediaType.APPLICATION_JSON).body(corps); }
        var reponse = requete.retrieve().onStatus(s -> s.isError(), (q, r) -> { }).toEntity(String.class);
        assertThat(reponse.getStatusCode().value()).as("%s %s", operation, reponse.getBody()).isEqualTo(statut);
        ContratApi.verifier(route.startsWith("/api/test/") ? "docs/api/prototype/openapi.yaml" : "docs/api/openapi.yaml", operation, reponse);
        return json.readTree(reponse.getBody());
    }

    private static JsonNode evenement(LinkedBlockingQueue<JsonNode> messages, String type) throws InterruptedException {
        var message = messages.poll(3, TimeUnit.SECONDS);
        assertThat(message).as(type).isNotNull();
        assertThat(message.path("type").asString()).isEqualTo(type);
        return message;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Temps {
        @Bean @Primary HorlogeFactice horlogeFreestyle() { return new HorlogeFactice(); }
    }
}
