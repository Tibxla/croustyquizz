package fr.croustyquizz.karaoke;

import static fr.croustyquizz.karaoke.ConfigurationTroisJoueurs.joueursDe;
import static fr.croustyquizz.karaoke.KaraokeHttpTest.verifier;
import static org.assertj.core.api.Assertions.assertThat;

import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.Acteur;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Catalogue d'essai servi et proposé par la vraie application. Nécessite Docker ; LRCLIB factice. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "croustyquizz.bibliotheque.racine=" + CatalogueDeBaseTest.ESSAI)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, ConfigurationTroisJoueurs.class})
class BibliothequeDeBaseHttpTest {
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired ServiceKaraoke service;
    final UUID soiree = UUID.randomUUID();
    final Acteur joueur = new Acteur(Acteur.Type.JOUEUR, joueursDe(soiree).getFirst(), soiree);
    final String chansonId = IdentifiantsChanson.encoderBibliotheque(1);

    @Test void laRechercheLaFicheEtLInscriptionDUneChansonDeLaBibliothequeSuiventLeContrat() throws Exception {
        String racine = "/api/soirees/" + soiree + "/manches/" + demarrer();
        var recherche = envoyer(HttpMethod.GET, racine + "/chansons?recherche=Lanterne&rechercheId=" + UUID.randomUUID(),
                joueur, null, null);
        verifier("rechercherChansons", recherche, 200);
        JsonNode chansons = json.readTree(recherche.getBody()).path("chansons");
        JsonNode lanterne = chansons.valueStream().filter(c -> c.path("chansonId").asString().equals(chansonId)).findFirst().orElseThrow();
        assertThat(lanterne.path("origine").asString()).isEqualTo("BIBLIOTHEQUE");
        assertThat(lanterne.path("bibliotheque").path("fichier").asString())
                .isEqualTo("/bibliotheque/chansons/les-lucioles-la-lanterne.wav");
        assertThat(chansons.valueStream().filter(c -> c.path("origine").asString().equals("LRCLIB")))
                .allSatisfy(c -> assertThat(c.path("bibliotheque").isNull()).isTrue());

        var fiche = envoyer(HttpMethod.GET, racine + "/chansons/" + chansonId, joueur, null, null);
        verifier("consulterChanson", fiche, 200);
        assertThat(json.readTree(fiche.getBody()).path("inscriptionPossible").asBoolean()).isTrue();

        var inscription = envoyer(HttpMethod.POST, racine + "/passages", joueur,
                Map.of("envoiId", UUID.randomUUID(), "chansonId", chansonId), null);
        verifier("inscrirePassage", inscription, 201);
    }

    @Test void lesFichiersSontServisAvecUnCacheLongEtLeCatalogueRevalide() {
        var client = RestClient.create("http://localhost:" + port);
        ResponseEntity<byte[]> son = client.get().uri("/bibliotheque/chansons/les-lucioles-la-lanterne.wav").retrieve().toEntity(byte[].class);
        assertThat(son.getStatusCode().value()).isEqualTo(200);
        assertThat(son.getBody()).hasSize(32044);
        assertThat(son.getHeaders().getCacheControl()).isEqualTo("max-age=31536000, public");
        ResponseEntity<String> lrc = client.get().uri("/bibliotheque/chansons/les-lucioles-la-lanterne.lrc").retrieve().toEntity(String.class);
        assertThat(lrc.getHeaders().getCacheControl()).isEqualTo("max-age=31536000, public");
        ResponseEntity<String> catalogue = client.get().uri("/bibliotheque/catalogue.json").retrieve().toEntity(String.class);
        assertThat(catalogue.getHeaders().getCacheControl()).isEqualTo("no-cache");
        assertThat(catalogue.getBody()).contains("\"morceaux\"");
        assertThat(client.get().uri("/bibliotheque/absent.mp3").retrieve().onStatus(s -> true, (q, r) -> { })
                .toBodilessEntity().getStatusCode().value()).isEqualTo(404);
    }

    UUID demarrer() throws Exception {
        UUID manche = service.creer(soiree, Map.of());
        KaraokeHttpTest.verifierPrototype("demarrerMancheDeTest", envoyer(HttpMethod.POST, "/api/test/soirees/" + soiree
                + "/manches/" + manche + "/demarrer", new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree), null,
                UUID.randomUUID()), 200);
        return manche;
    }

    ResponseEntity<String> envoyer(HttpMethod methode, String route, Acteur acteur, Object corps, UUID id) {
        var requete = RestClient.create("http://localhost:" + port).method(methode).uri(route);
        if (acteur != null) { requete.header("Authorization", "Bearer demo:" + acteur.type() + ":" + acteur.soireeId() + ":" + acteur.identifiant()); }
        if (id != null) { requete.header("Idempotency-Key", id.toString()).header("X-CSRF-TOKEN", "demonstration"); }
        if (corps != null) { requete.contentType(MediaType.APPLICATION_JSON).body(corps); }
        return requete.retrieve().onStatus(s -> s.isError(), (q, r) -> { }).toEntity(String.class);
    }
}
