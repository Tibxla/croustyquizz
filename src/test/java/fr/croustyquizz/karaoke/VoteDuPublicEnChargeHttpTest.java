package fr.croustyquizz.karaoke;

import static fr.croustyquizz.Attentes.PLAFOND;
import static org.assertj.core.api.Assertions.assertThat;

import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.HorlogeFactice;
import fr.croustyquizz.tempsreel.Canaux;
import fr.croustyquizz.tempsreel.ClientDeSoiree;
import fr.croustyquizz.tempsreel.ClientDeSoiree.JoueurArrive;
import fr.croustyquizz.tempsreel.ClientDeSoiree.SoireeOuverte;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Vote du public à soixante Manettes (#263), sur le modèle du Buzz de Soirée (#8) : à la fin d'un Passage, toute la
 * salle vote dans les mêmes instants, sous le verrou de la Soirée, et chaque vote publie une vue. Soirée et Joueurs
 * sont les vrais ; seules l'horloge et la source de paroles sont factices. Exige Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, KaraokeSurSoireeEnregistreeHttpTest.SourceLocale.class,
        VoteDuPublicEnChargeHttpTest.TempsDuVote.class})
class VoteDuPublicEnChargeHttpTest {
    /** L'Interprète ne vote pas pour son Passage : la Soirée compte soixante votants et lui. */
    static final int VOTANTS = 60;
    /** 25 × 5 + 20 × 4 + 10 × 2 + 5 × 1 = 230 sur 60 votes : 3,833…, publiée en dixièmes arrondis. */
    static final int MOYENNE_DIXIEMES = 38;
    private static final Logger JOURNAL = LoggerFactory.getLogger(VoteDuPublicEnChargeHttpTest.class);

    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired HorlogeFactice horloge;
    @Autowired ProprietesKaraoke proprietes;
    ClientDeSoiree client;

    @TestConfiguration(proxyBeanMethods = false)
    static class TempsDuVote {
        @Bean @Primary HorlogeFactice horlogeDuVote() { return new HorlogeFactice(); }
    }

    @BeforeEach void ouvrirLeClient() { client = new ClientDeSoiree(port, json); }

    @AfterEach void fermerLesConnexions() { client.close(); }

    @Test
    @Timeout(60)
    void soixanteVotesEnRafaleSontTousComptesEtLaClotureLesPublieAToutesLesManettes(TestReporter compteRendu)
            throws Exception {
        SoireeOuverte soiree = client.ouvrirSoiree();
        JoueurArrive interprete = client.rejoindre(soiree, "Interprète");
        List<JoueurArrive> joueurs = new ArrayList<>();
        for (int i = 1; i <= VOTANTS; i++) { joueurs.add(client.rejoindre(soiree, "Joueur " + i)); }
        String manche = lancerUnKaraoke(soiree);
        JsonNode inscription = client.verifier("inscrirePassage", client.envoyer(HttpMethod.POST, manche + "/passages",
                interprete.jeton(), Map.of("envoiId", UUID.randomUUID(), "chansonId", IdentifiantsChanson.encoder(1)), null), 201);
        String passageId = inscription.path("passageId").asString();
        String route = manche + "/passages/" + passageId;
        JsonNode appel = client.verifier("appelerPassage", client.envoyer(HttpMethod.POST, route + "/appel",
                soiree.animateur(), Map.of("numeroModification", 0), UUID.randomUUID()), 200);

        var salle = abonner(soiree, "salle", soiree.cleEcran(), Canaux.salle(soiree.id()));
        var console = abonner(soiree, "console", soiree.animateur(), Canaux.console(soiree.id()));
        var manetteInterprete = abonner(soiree, "manette", interprete.jeton(), Canaux.joueur(interprete.id()));
        List<Votant> votants = new ArrayList<>();
        for (int i = 0; i < VOTANTS; i++) {
            JoueurArrive joueur = joueurs.get(i);
            votants.add(new Votant(joueur, note(i), UUID.randomUUID(),
                    abonner(soiree, "manette", joueur.jeton(), Canaux.joueur(joueur.id()))));
        }
        List<Abonne> destinations = new ArrayList<>(List.of(salle, console, manetteInterprete));
        votants.forEach(votant -> destinations.add(votant.manette()));

        client.verifier("confirmerPresencePassage", client.envoyer(HttpMethod.POST, route + "/presence", interprete.jeton(),
                Map.of("envoiId", UUID.randomUUID(), "etapeId", appel.path("etapeId").asString(), "numeroModification", 1),
                null), 200);
        for (var abonne : destinations) { lire(abonne, "passage-demarre"); }
        horloge.avancer(Duration.ofMillis(ServiceKaraokeTest.contenu(1).chanson().dureeMs()));
        for (var abonne : destinations) {
            assertThat(lire(abonne, "passage-termine").at("/karaoke/votes/0/etat").asString()).isEqualTo("OUVERT");
        }
        long numero = capturer(soiree, votants.getFirst().manette()).at("/vue/karaoke/votes/0/numeroModification").asLong();

        // L'horloge factice reste arrêtée pendant la rafale : aucun vote ne peut arriver après l'échéance.
        var rafale = ClientDeSoiree.enRafale(votants, votant -> client.envoyer(HttpMethod.POST, route + "/vote",
                votant.joueur().jeton(), Map.of("envoiId", votant.envoiId(), "numeroModification", numero, "note", votant.note()),
                null));
        // Mesure sans seuil : Surefire n'écrit pas les entrées de TestReporter, le journal les porte dans son rapport.
        compteRendu.publishEntry("dureeRafaleVotesMs", Long.toString(rafale.duree().toMillis()));
        JOURNAL.info("Rafale de {} votes : {} ms du départ au dernier accusé.", VOTANTS, rafale.duree().toMillis());
        for (int i = 0; i < VOTANTS; i++) {
            Votant votant = votants.get(i);
            JsonNode accuse = client.verifier("voterPassage", rafale.reponses().get(i), 200);
            assertThat(rafale.reponses().get(i).getHeaders().getCacheControl()).isEqualTo("no-store");
            assertThat(accuse.path("envoiId").asString()).isEqualTo(votant.envoiId().toString());
            assertThat(accuse.path("passageId").asString()).isEqualTo(passageId);
            assertThat(accuse.path("note").asInt()).isEqualTo(votant.note());
        }
        for (var abonne : List.of(salle, console)) {
            long revisionPrecedente = -1;
            for (int compte = 1; compte <= VOTANTS; compte++) {
                JsonNode evenement = lireEvenement(abonne, "vote-enregistre");
                assertThat(evenement.at("/donnees/karaoke/votes/0/nombreVotes").asInt()).as("%s, vote %s", abonne.ecran(), compte)
                        .isEqualTo(compte);
                assertThat(evenement.at("/donnees/karaoke/votes/0/moyenneDixiemes").isNull()).isTrue();
                if (revisionPrecedente >= 0) { assertThat(evenement.path("revision").asLong()).isEqualTo(revisionPrecedente + 1); }
                revisionPrecedente = evenement.path("revision").asLong();
            }
        }
        for (var votant : votants) {
            assertThat(lire(votant.manette(), "vote-enregistre").at("/karaoke/votes/0/maNote").asInt()).isEqualTo(votant.note());
        }

        horloge.avancer(Duration.ofSeconds(proprietes.passage().dureeVoteSecondes()));
        // Une seule échéance pour toutes les destinations : chacune reçoit la vue finale dans le plafond de la clôture.
        long echeance = System.nanoTime() + PLAFOND.toNanos();
        List<JsonNode> vuesFinales = new ArrayList<>();
        for (var abonne : destinations) { vuesFinales.add(lireEvenement(abonne, "vote-clos", echeance)); }
        for (int i = 0; i < destinations.size(); i++) {
            Abonne abonne = destinations.get(i);
            JsonNode vote = verifierVueFinale(soiree, abonne, vuesFinales.get(i)).at("/karaoke/votes/0");
            assertThat(vote.path("etat").asString()).isEqualTo("CLOS");
            assertThat(vote.path("nombreVotes").asInt()).as(abonne.ecran()).isEqualTo(VOTANTS);
            assertThat(vote.path("moyenneDixiemes").asInt()).as(abonne.ecran()).isEqualTo(MOYENNE_DIXIEMES);
        }
        for (var votant : votants) {
            JsonNode capture = capturer(soiree, votant.manette());
            assertThat(capture.at("/vue/karaoke/votes/0/maNote").asInt()).isEqualTo(votant.note());
        }
        assertThat(capturer(soiree, manetteInterprete).at("/vue/karaoke/votes/0/maNote").isNull()).isTrue();
        for (var abonne : destinations) { assertThat(abonne.file()).as(abonne.ecran()).isEmpty(); }
    }

    /** Notes dont la moyenne n'est pas entière : 25 fois 5, 20 fois 4, 10 fois 2 et 5 fois 1. */
    static int note(int rang) {
        if (rang < 25) { return 5; }
        if (rang < 45) { return 4; }
        return rang < 55 ? 2 : 1;
    }

    String lancerUnKaraoke(SoireeOuverte soiree) throws Exception {
        JsonNode creation = client.verifier("ajouterManche", client.envoyer(HttpMethod.POST, soiree.route() + "/manches",
                soiree.animateur(), Map.of("mode", "KARAOKE", "reglages", Map.of()), null), 201);
        client.verifier("piloterSoiree", client.envoyer(HttpMethod.POST, soiree.route() + "/commandes", soiree.animateur(),
                Map.of("action", "LANCER"), UUID.randomUUID()), 200);
        return soiree.route() + "/manches/" + creation.path("id").asString();
    }

    Abonne abonner(SoireeOuverte soiree, String ecran, String secret, String destination) throws Exception {
        return new Abonne(ecran, secret, client.abonner(soiree, ecran, secret, destination));
    }

    JsonNode lireEvenement(Abonne abonne, String type) throws InterruptedException {
        return lireEvenement(abonne, type, System.nanoTime() + PLAFOND.toNanos());
    }

    /** Prochain événement de la destination, attendu jusqu'à l'échéance donnée en {@code System.nanoTime()}. */
    JsonNode lireEvenement(Abonne abonne, String type, long echeance) throws InterruptedException {
        JsonNode evenement = abonne.file().poll(Math.max(0, echeance - System.nanoTime()), TimeUnit.NANOSECONDS);
        assertThat(evenement).as("%s attend %s", abonne.ecran(), type).isNotNull();
        assertThat(evenement.path("type").asString()).as(abonne.ecran()).isEqualTo(type);
        return evenement;
    }

    JsonNode lire(Abonne abonne, String type) throws InterruptedException {
        return lireEvenement(abonne, type).path("donnees");
    }

    /** Dernier événement attendu : la capture de la destination doit rendre la même vue, à la même révision. */
    JsonNode verifierVueFinale(SoireeOuverte soiree, Abonne abonne, JsonNode evenement) throws Exception {
        JsonNode capture = capturer(soiree, abonne);
        assertThat(capture.path("revision")).as(abonne.ecran()).isEqualTo(evenement.path("revision"));
        assertThat(capture.path("vue")).as(abonne.ecran()).isEqualTo(evenement.path("donnees"));
        return evenement.path("donnees");
    }

    JsonNode capturer(SoireeOuverte soiree, Abonne abonne) throws Exception {
        String operation = "retrouverEtat" + Character.toUpperCase(abonne.ecran().charAt(0)) + abonne.ecran().substring(1);
        return client.verifier(operation, client.envoyer(HttpMethod.GET, soiree.route() + "/etat/" + abonne.ecran(),
                abonne.secret(), null, null), 200);
    }

    record Abonne(String ecran, String secret, BlockingQueue<JsonNode> file) { }
    record Votant(JoueurArrive joueur, int note, UUID envoiId, Abonne manette) { }
}
