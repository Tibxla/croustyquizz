package fr.croustyquizz.karaoke;

import static fr.croustyquizz.karaoke.KaraokeJpaTest.enregistrerPourPalmares;
import static fr.croustyquizz.karaoke.KaraokeJpaTest.passagePourPalmares;
import static fr.croustyquizz.karaoke.KaraokeJpaTest.votePourPalmares;
import static org.assertj.core.api.Assertions.assertThat;

import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import fr.croustyquizz.tempsreel.AssembleurDeVues;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("recette")
@Import({TestcontainersConfiguration.class, ConfigurationTroisJoueurs.class})
class FournisseurPalmaresKaraokeTest {
    private static final Instant FIN = Instant.parse("2026-11-18T21:30:00Z");
    private final UUID soiree = UUID.randomUUID();
    @Autowired SocleDeManche socle;
    @Autowired AssembleurDeVues assembleur;
    @Autowired PersistanceKaraokeJpa persistance;
    @Autowired ServiceKaraoke service;

    @Test void attendUneFinDeKaraokeEtMasqueLePalmaresPendantUneMancheOuApresUnQuiz() {
        assertThat(palmares(Destination.SALLE).isNull()).isTrue();
        var premiere = new UUID(117, 1);
        manche(premiere, ModeDeJeu.KARAOKE, EtatManche.TERMINEE, FIN);
        assertThat(palmares(Destination.SALLE).isArray()).isTrue();
        assertThat(palmares(Destination.SALLE).size()).isZero();
        assertThat(palmares(Destination.CONSOLE).isArray()).isTrue();
        var seconde = new UUID(117, 2);
        manche(seconde, ModeDeJeu.QUIZ, EtatManche.EN_COURS, FIN.plusSeconds(1));
        assertThat(palmares(Destination.SALLE).isNull()).isTrue();
        manche(seconde, ModeDeJeu.QUIZ, EtatManche.EN_PAUSE, FIN.plusSeconds(2));
        assertThat(palmares(Destination.CONSOLE).isNull()).isTrue();
        // À heure égale, le plus grand identifiant désigne la dernière Manche terminée.
        manche(seconde, ModeDeJeu.QUIZ, EtatManche.TERMINEE, FIN);
        assertThat(palmares(Destination.SALLE).isNull()).isTrue();
        manche(premiere, ModeDeJeu.KARAOKE, EtatManche.TERMINEE, FIN.plusSeconds(3));
        assertThat(palmares(Destination.SALLE).isArray()).isTrue();
    }

    @Test void reunitLesPassagesDesDeuxManchesEtReserveLesTroisClassesALaSalle() {
        var premiere = UUID.randomUUID();
        var seconde = UUID.randomUUID();
        manche(premiere, ModeDeJeu.KARAOKE, EtatManche.TERMINEE, FIN.minusSeconds(60));
        manche(seconde, ModeDeJeu.KARAOKE, EtatManche.TERMINEE, FIN);
        UUID parti = UUID.randomUUID(), present = ConfigurationTroisJoueurs.joueursDe(soiree).getFirst();
        var transaction = new TransactionTemplate(socle.transactions());
        transaction.executeWithoutResult(statut -> {
            enregistrer(premiere, 1, List.of(parti, present), 4, 5);
            enregistrer(seconde, 3, List.of(present), 3, 0);
        });

        assertThat(service.palmaresKaraoke(soiree)).extracting(VuesKaraoke.LignePalmaresKaraoke::numero)
                .containsExactly(2L, 1L, 3L, 4L);
        var console = palmares(Destination.CONSOLE);
        assertThat(console.size()).isEqualTo(4);
        assertThat(console.get(1).at("/interpretes/0/pseudo").asString()).isEqualTo("Joueur parti");
        assertThat(console.get(1).at("/interpretes/1/joueurId").asString()).isEqualTo(present.toString());
        assertThat(console.get(1).at("/chanson/titre").asString()).isEqualTo("Chanson 1");
        assertThat(console.get(3).path("rang").isNull()).isTrue();
        assertThat(console.get(3).path("moyenneDixiemes").isNull()).isTrue();
        var salle = palmares(Destination.SALLE);
        assertThat(salle.size()).isEqualTo(3);
        for (int i = 0; i < salle.size(); i++) {
            assertThat(salle.get(i)).isEqualTo(console.get(i));
        }
        assertThat(palmares(Destination.joueur(present)).isMissingNode()).isTrue();
    }

    private void manche(UUID id, ModeDeJeu mode, EtatManche etat, Instant modifieLe) {
        var transaction = new TransactionTemplate(socle.transactions());
        transaction.executeWithoutResult(statut -> {
            var manche = socle.depot().charger(id);
            if (manche == null) {
                String reglages = mode == ModeDeJeu.KARAOKE ? "{\"limitePassagesEnAttente\":2}"
                        : "{\"nombreEtapes\":1,\"tempsParEtapeSecondes\":20}";
                manche = new ManchePersistante(id, soiree, mode, reglages, FIN.minusSeconds(120));
            }
            manche.changerEtat(etat, modifieLe);
            socle.depot().enregistrer(manche);
        });
    }

    private void enregistrer(UUID manche, int numero, List<UUID> interpretes, int note, int noteSuivante) {
        var premier = passagePourPalmares(soiree, manche, numero, interpretes, EtatPassage.TERMINE, FIN.minusSeconds(20));
        var suivant = passagePourPalmares(soiree, manche, numero + 1L, interpretes, EtatPassage.TERMINE, FIN.minusSeconds(10));
        enregistrerPourPalmares(persistance, List.of(premier, suivant),
                List.of(votePourPalmares(premier, FIN, notes(note)), votePourPalmares(suivant, FIN, notes(noteSuivante))));
    }

    private static List<VoteRecu> notes(int note) {
        return note == 0 ? List.of() : List.of(new VoteRecu(UUID.randomUUID(), note, FIN.minusSeconds(1)));
    }

    private JsonNode palmares(Destination destination) {
        return socle.json().valueToTree(assembleur.assembler(soiree, destination, "manche-terminee").champs())
                .path("palmaresKaraoke");
    }
}
