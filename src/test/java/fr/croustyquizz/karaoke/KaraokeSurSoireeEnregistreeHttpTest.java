package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.assertThat;

import fr.croustyquizz.ContratApi;
import fr.croustyquizz.SessionAnimateurHttp;
import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.EtatDeSoiree;
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
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
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
    private SessionAnimateurHttp animateur;

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
        String manche = demarrerKaraoke(soiree, null);

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
        String manche = demarrerKaraoke(soiree, null);
        var refus = envoyer(HttpMethod.POST, "/api/soirees/" + soiree + "/manches/" + manche + "/passages", jeton,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null);
        KaraokeHttpTest.verifier("inscrirePassage", refus, 409);
        assertThat(json.readTree(refus.getBody()).path("code").asString()).isEqualTo("ETAPE_INACTIVE");
    }

    @Test void uneSoireeDeDemonstrationPasseParLeRepliCommeAvant() throws Exception {
        UUID demonstration = UUID.randomUUID();
        String joueur = "demo:JOUEUR:" + demonstration + ":" + FournisseurCommunDemonstration.joueurId(demonstration);
        String manche = demarrerKaraoke(demonstration.toString(), "demo:ANIMATEUR:" + demonstration + ":" + UUID.randomUUID());
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

    String soireeOuverte() throws Exception {
        animateur = SessionAnimateurHttp.ouvrir(port, json);
        var creation = envoyer(HttpMethod.POST, "/api/soirees", null,
                Map.of("titre", "Karaoké du vendredi", "format", "SOLO", "pilotage", "MANUEL"), null);
        ContratApi.verifier("docs/api/openapi.yaml", "creerSoiree", creation);
        assertThat(creation.getStatusCode().value()).isEqualTo(201);
        String id = json.readTree(creation.getBody()).path("id").asString();
        var ouverture = envoyer(HttpMethod.POST, "/api/soirees/" + id + "/ouverture", null, null, UUID.randomUUID());
        ContratApi.verifier("docs/api/openapi.yaml", "ouvrirSoiree", ouverture);
        assertThat(ouverture.getStatusCode().value()).isEqualTo(200);
        return id;
    }

    JsonNode rejoindre(String soiree, String pseudo) throws Exception {
        var lecture = envoyer(HttpMethod.GET, "/api/soirees/" + soiree, null, null, null);
        ContratApi.verifier("docs/api/openapi.yaml", "consulterSoiree", lecture);
        String code = json.readTree(lecture.getBody()).path("codeAcces").asString();
        var arrivee = envoyer(HttpMethod.POST, "/api/soirees/rejoindre", null, Map.of("codeAcces", code, "pseudo", pseudo), null);
        ContratApi.verifier("docs/api/openapi.yaml", "rejoindreSoiree", arrivee);
        assertThat(arrivee.getStatusCode().value()).isEqualTo(201);
        return json.readTree(arrivee.getBody());
    }

    String demarrerKaraoke(String soiree, String secret) throws Exception {
        var creation = envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree + "/manches", secret,
                Map.of("mode", "KARAOKE", "reglages", Map.of()), null);
        KaraokeHttpTest.verifierPrototype("creerMancheDeTest", creation, 201);
        String manche = json.readTree(creation.getBody()).path("mancheId").asString();
        KaraokeHttpTest.verifierPrototype("demarrerMancheDeTest", envoyer(HttpMethod.POST,
                "/api/test/soirees/" + soiree + "/manches/" + manche + "/demarrer", secret, null, UUID.randomUUID()), 200);
        return manche;
    }

    ResponseEntity<String> envoyer(HttpMethod methode, String route, String secret, Object corps, UUID cle) {
        var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route);
        if (secret != null) { requete.header("Authorization", "Bearer " + secret); }
        if (secret == null && !route.equals("/api/soirees/rejoindre")) {
            requete.headers(entetes -> entetes.addAll(animateur.entetes()));
        }
        if (cle != null) { requete.header("Idempotency-Key", cle.toString()); }
        if (corps != null) { requete.contentType(MediaType.APPLICATION_JSON).body(corps); }
        return requete.retrieve().onStatus(s -> s.isError(), (q, r) -> { }).toEntity(String.class);
    }

}
