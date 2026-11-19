package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.assertThat;

import fr.croustyquizz.TestcontainersConfiguration;
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
 * paroles est factice ; Soirée, Joueurs, portée et pseudos sont les vrais.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, KaraokeSurSoireeEnregistreeHttpTest.SourceLocale.class})
class KaraokeSurSoireeEnregistreeHttpTest {
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;

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
        JsonNode lea = rejoindre(soiree, "Léa");
        String jeton = lea.path("jetonReconnexion").asString();
        String manche = demarrerKaraoke(soiree);

        var inscription = envoyer(HttpMethod.POST, "/api/soirees/" + soiree + "/manches/" + manche + "/passages", jeton,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null);
        KaraokeHttpTest.verifier("inscrirePassage", inscription, 201);

        var manette = envoyer(HttpMethod.GET, "/api/soirees/" + soiree + "/etat/manette", jeton, null, null);
        assertThat(manette.getStatusCode().value()).isEqualTo(200);
        JsonNode karaoke = json.readTree(manette.getBody()).path("vue").path("karaoke");
        assertThat(karaoke.path("mesPassagesEnAttente")).hasSize(1);
        assertThat(karaoke.toString()).contains("\"pseudo\":\"Léa\"").doesNotContain("Joueur parti");
    }

    @Test void unJoueurDUneAutreSoireeNEntrePasDansLaManche() throws Exception {
        String soiree = soireeOuverte(), autre = soireeOuverte();
        String jetonAutre = rejoindre(autre, "Tom").path("jetonReconnexion").asString();
        String manche = demarrerKaraoke(soiree);
        var refus = envoyer(HttpMethod.POST, "/api/soirees/" + soiree + "/manches/" + manche + "/passages", jetonAutre,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null);
        KaraokeHttpTest.verifier("inscrirePassage", refus, 404);
    }

    @Test void uneMancheDUneAutreSoireeResteIntrouvable() throws Exception {
        String soiree = soireeOuverte(), autre = soireeOuverte();
        String jeton = rejoindre(soiree, "Léa").path("jetonReconnexion").asString();
        String mancheAutre = demarrerKaraoke(autre);
        var refus = envoyer(HttpMethod.POST, "/api/soirees/" + soiree + "/manches/" + mancheAutre + "/passages", jeton,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null);
        KaraokeHttpTest.verifier("inscrirePassage", refus, 404);
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
