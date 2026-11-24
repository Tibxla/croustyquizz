package fr.croustyquizz.passage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClassementParVoteTest {
    @Test void classeParMoyenneExacteAvantLeNombreDeVotes() {
        var premier = resultat(12, 3, 13, 43);
        var second = resultat(9, 4, 17, 43);
        assertThat(ClassementParVote.classer(List.of(second, premier)))
                .containsExactly(new RangPassage(premier, 1L), new RangPassage(second, 2L));
    }

    @Test void departageParNombrePuisNumeroEtPlaceLesPassagesSansVoteEnFin() {
        var premier = resultat(4, 4, 14, 35);
        var second = resultat(2, 2, 7, 35);
        var troisieme = resultat(3, 2, 7, 35);
        var sansVote = resultat(1, 0, 0, null);
        var autreSansVote = resultat(5, 0, 0, null);
        assertThat(ClassementParVote.classer(List.of(autreSansVote, troisieme, second, sansVote, premier)))
                .containsExactly(new RangPassage(premier, 1L), new RangPassage(second, 2L),
                        new RangPassage(troisieme, 3L), new RangPassage(sansVote, null), new RangPassage(autreSansVote, null));
    }

    @Test void uneListeVideDonneUnClassementVide() {
        assertThat(ClassementParVote.classer(List.of())).isEmpty();
    }

    private static ResultatVote resultat(long numero, long nombre, long somme, Integer moyenne) {
        return new ResultatVote(UUID.randomUUID(), UUID.randomUUID(), numero, List.of(UUID.randomUUID()), nombre, somme, moyenne);
    }
}
