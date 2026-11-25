package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.EtatDeSoiree;
import fr.croustyquizz.passage.EtatPassage;
import fr.croustyquizz.passage.ParticipantsDePassage;
import fr.croustyquizz.tempsreel.FournisseurCommunDemonstration;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Karaoké sur une Soirée enregistrée (#111) : les ports de {@code passage} lisent la
 * Soirée et ses Joueurs en base, pas la forme JSON de la vue. Seule la source de
 * paroles est factice ; Soirée, Joueurs, portée et pseudos sont les vrais. Une Soirée de
 * démonstration, absente de la base, passe toujours par le repli {@code @Fallback}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, KaraokeSurSoireeEnregistreeHttpTest.SourceLocale.class})
class KaraokeSurSoireeEnregistreeHttpTest {
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EtatDeSoiree etatDeSoiree;
    @Autowired ServiceKaraoke service;
    @MockitoSpyBean PersistanceKaraokeJpa persistance;

    @TestConfiguration(proxyBeanMethods = false)
    static class SourceLocale {
        @Bean @Primary SourceDeParoles sourceLocale() {
            return new SourceDeParoles() {
                public List<Chanson> rechercher(String recherche) { return List.of(ServiceKaraokeTest.contenu(1).chanson()); }
                public Optional<ContenuKaraoke> charger(long id) { return Optional.of(ServiceKaraokeTest.contenu(id)); }
            };
        }
    }

    @BeforeEach void effacerLesSoirees() {
        jdbc.update("delete from acces_ecran");
        jdbc.update("delete from joueur");
        jdbc.update("delete from soiree");
    }

    @Test void unJoueurArriveParCodeSInscritEtSonPseudoApparait() throws Exception {
        String soiree = soireeOuverte();
        String jeton = rejoindre(soiree, "Léa").path("jetonReconnexion").asString();
        lancer(soiree);
        String manche = demarrerKaraoke(soiree);

        var inscription = envoyer(HttpMethod.POST, "/api/soirees/" + soiree + "/manches/" + manche + "/passages", jeton,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null);
        KaraokeHttpTest.verifier("inscrirePassage", inscription, 201);

        var manette = envoyer(HttpMethod.GET, "/api/soirees/" + soiree + "/etat/manette", jeton, null, null);
        ContratApi.verifier("docs/api/openapi.yaml", "retrouverEtatManette", manette);
        assertThat(manette.getStatusCode().value()).isEqualTo(200);
        JsonNode karaoke = json.readTree(manette.getBody()).path("vue").path("karaoke");
        assertThat(karaoke.path("mesPassagesEnAttente")).hasSize(1);
        assertThat(karaoke.toString()).contains("\"pseudo\":\"Léa\"").doesNotContain(ParticipantsDePassage.JOUEUR_PARTI);
    }

    @Test void uneSoireeSeulementOuverteNeFaitPasTournerLaFile() throws Exception {
        String soiree = soireeOuverte();
        String jeton = rejoindre(soiree, "Léa").path("jetonReconnexion").asString();
        String manche = demarrerKaraoke(soiree);
        var refus = envoyer(HttpMethod.POST, "/api/soirees/" + soiree + "/manches/" + manche + "/passages", jeton,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null);
        KaraokeHttpTest.verifier("inscrirePassage", refus, 409);
        assertThat(json.readTree(refus.getBody()).path("code").asString()).isEqualTo("ETAPE_INACTIVE");
    }

    @Test void terminerLaSoireeAnnuleLePassageActifSansViserSonEtape() throws Exception {
        String soiree = soireeOuverte();
        String animateur = "demo:ANIMATEUR:" + soiree + ":" + UUID.randomUUID();
        String joueur = rejoindre(soiree, "Léa").path("jetonReconnexion").asString();
        String racine = "/api/soirees/" + soiree;
        var creation = envoyer(HttpMethod.POST, racine + "/manches", animateur, Map.of("mode", "KARAOKE", "reglages", Map.of()), null);
        KaraokeHttpTest.verifier("ajouterManche", creation, 201);
        String manche = json.readTree(creation.getBody()).path("id").asString();
        String routeManche = racine + "/manches/" + manche;
        UUID clePause = UUID.randomUUID();
        var pauseAvantLancement = envoyer(HttpMethod.POST, routeManche + "/commandes", animateur,
                Map.of("action", "METTRE_EN_PAUSE"), clePause);
        KaraokeHttpTest.verifier("piloterManche", pauseAvantLancement, 409);
        assertThat(json.readTree(pauseAvantLancement.getBody()).path("code").asString()).isEqualTo("ETAT_INCOMPATIBLE");
        KaraokeHttpTest.verifier("piloterSoiree", envoyer(HttpMethod.POST, racine + "/commandes", animateur,
                Map.of("action", "LANCER"), UUID.randomUUID()), 200);
        var pauseRejouee = envoyer(HttpMethod.POST, routeManche + "/commandes", animateur,
                Map.of("action", "METTRE_EN_PAUSE"), clePause);
        KaraokeHttpTest.verifier("piloterManche", pauseRejouee, 409);
        assertThat(json.readTree(pauseRejouee.getBody())).isEqualTo(json.readTree(pauseAvantLancement.getBody()));

        var inscription = envoyer(HttpMethod.POST, routeManche + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null);
        KaraokeHttpTest.verifier("inscrirePassage", inscription, 201);
        String passageId = json.readTree(inscription.getBody()).path("passageId").asString();
        KaraokeHttpTest.verifier("appelerPassage", envoyer(HttpMethod.POST, routeManche + "/passages/" + passageId + "/appel",
                animateur, Map.of("numeroModification", 0), UUID.randomUUID()), 200);
        var sansEtape = envoyer(HttpMethod.POST, routeManche + "/commandes", animateur,
                Map.of("action", "TERMINER"), UUID.randomUUID());
        KaraokeHttpTest.verifier("piloterManche", sansEtape, 409);
        assertThat(json.readTree(sansEtape.getBody()).path("code").asString()).isEqualTo("ETAPE_INACTIVE");

        KaraokeHttpTest.verifier("piloterSoiree", envoyer(HttpMethod.POST, racine + "/commandes", animateur,
                Map.of("action", "TERMINER"), UUID.randomUUID()), 200);
        var instantane = (InstantaneKaraoke) service.instantane(UUID.fromString(soiree), UUID.fromString(manche));
        assertThat(instantane.file().passages()).singleElement().satisfies(passage ->
                assertThat(passage.etat()).isEqualTo(EtatPassage.ANNULE));
        assertThat(etatDeSoiree.estTerminee(UUID.fromString(soiree))).isTrue();
    }

    @Test void leMemeEnvoiReprendUnLancementDontLaPremiereMancheNApasPuDemarrer() throws Exception {
        String soiree = soireeOuverte();
        String animateur = "demo:ANIMATEUR:" + soiree + ":" + UUID.randomUUID();
        String racine = "/api/soirees/" + soiree;
        var creation = envoyer(HttpMethod.POST, racine + "/manches", animateur, Map.of("mode", "KARAOKE", "reglages", Map.of()), null);
        KaraokeHttpTest.verifier("ajouterManche", creation, 201);
        UUID manche = UUID.fromString(json.readTree(creation.getBody()).path("id").asString());
        UUID cle = UUID.randomUUID();
        var lancer = Map.of("action", "LANCER");
        doThrow(new DataAccessResourceFailureException("Panne factice")).when(persistance).enregistrer(eq(manche), any());
        try {
            KaraokeHttpTest.verifier("piloterSoiree", envoyer(HttpMethod.POST, racine + "/commandes", animateur, lancer, cle), 503);
        } finally {
            doCallRealMethod().when(persistance).enregistrer(eq(manche), any());
        }
        assertThat(etatDeSoiree.estEnCours(UUID.fromString(soiree))).isTrue();
        KaraokeHttpTest.verifier("piloterSoiree", envoyer(HttpMethod.POST, racine + "/commandes", animateur, lancer, UUID.randomUUID()), 409);
        var reprise = envoyer(HttpMethod.POST, racine + "/commandes", animateur, lancer, cle);
        KaraokeHttpTest.verifier("piloterSoiree", reprise, 200);
        assertThat(json.readTree(reprise.getBody()).path("envoiId").asString()).isEqualTo(cle.toString());
        var capture = envoyer(HttpMethod.GET, racine + "/etat/console", animateur, null, null);
        KaraokeHttpTest.verifier("retrouverEtatConsole", capture, 200);
        assertThat(json.readTree(capture.getBody()).at("/vue/mancheActive/id").asString()).isEqualTo(manche.toString());
    }

    @Test void uneSoireeDeDemonstrationPasseParLeRepliCommeAvant() throws Exception {
        UUID demonstration = UUID.randomUUID();
        String joueur = "demo:JOUEUR:" + demonstration + ":" + FournisseurCommunDemonstration.joueurId(demonstration);
        String manche = demarrerKaraoke(demonstration.toString());
        var inscription = envoyer(HttpMethod.POST, "/api/soirees/" + demonstration + "/manches/" + manche + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null);
        KaraokeHttpTest.verifier("inscrirePassage", inscription, 201);
        var manette = envoyer(HttpMethod.GET, "/api/soirees/" + demonstration + "/etat/manette", joueur, null, null);
        ContratApi.verifier("docs/api/openapi.yaml", "retrouverEtatManette", manette);
        assertThat(json.readTree(manette.getBody()).path("vue").path("karaoke").toString()).contains("Joueur de démonstration");
        assertThat(etatDeSoiree.estEnCours(demonstration)).isTrue();
        assertThat(etatDeSoiree.estTerminee(demonstration)).isFalse();
    }

    /** Le lancement d'une Soirée arrive avec #12 : on le simule en base. */
    void lancer(String soiree) {
        jdbc.update("update soiree set etat = 'EN_COURS', debutee_le = now() where id = ?::uuid", soiree);
    }

    String soireeOuverte() {
        String id = json.readTree(envoyer(HttpMethod.POST, "/api/soirees", null,
                Map.of("titre", "Karaoké du vendredi", "format", "SOLO", "pilotage", "MANUEL"), null).getBody()).path("id").asString();
        assertThat(envoyer(HttpMethod.POST, "/api/soirees/" + id + "/ouverture", null, null, UUID.randomUUID())
                .getStatusCode().is2xxSuccessful()).isTrue();
        return id;
    }

    JsonNode rejoindre(String soiree, String pseudo) {
        String code = json.readTree(envoyer(HttpMethod.GET, "/api/soirees/" + soiree, null, null, null).getBody())
                .path("codeAcces").asString();
        var arrivee = envoyer(HttpMethod.POST, "/api/soirees/rejoindre", null, Map.of("codeAcces", code, "pseudo", pseudo), null);
        assertThat(arrivee.getStatusCode().value()).isEqualTo(201);
        return json.readTree(arrivee.getBody());
    }

    String demarrerKaraoke(String soiree) throws Exception {
        String animateur = "demo:ANIMATEUR:" + soiree + ":" + UUID.randomUUID();
        var creation = envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree + "/manches", animateur,
                Map.of("mode", "KARAOKE", "reglages", Map.of()), null);
        KaraokeHttpTest.verifierPrototype("creerMancheDeTest", creation, 201);
        String manche = json.readTree(creation.getBody()).path("mancheId").asString();
        KaraokeHttpTest.verifierPrototype("demarrerMancheDeTest", envoyer(HttpMethod.POST,
                "/api/test/soirees/" + soiree + "/manches/" + manche + "/demarrer", animateur, null, UUID.randomUUID()), 200);
        return manche;
    }

    ResponseEntity<String> envoyer(HttpMethod methode, String route, String secret, Object corps, UUID cle) {
        var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route);
        if (secret != null) { requete.header("Authorization", "Bearer " + secret); }
        if (cle != null) { requete.header("Idempotency-Key", cle.toString()).header("X-CSRF-TOKEN", "demonstration"); }
        if (corps != null) { requete.contentType(MediaType.APPLICATION_JSON).body(corps); }
        return requete.retrieve().onStatus(s -> s.isError(), (q, r) -> { }).toEntity(String.class);
    }

}
