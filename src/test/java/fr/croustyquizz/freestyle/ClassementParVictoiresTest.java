package fr.croustyquizz.freestyle;

import static org.assertj.core.api.Assertions.assertThat;

import fr.croustyquizz.passage.ResultatVote;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClassementParVictoiresTest {
    private static final UUID MANCHE = new UUID(0, 1);
    private static final UUID INES = new UUID(0, 2), MAEL = new UUID(0, 3), ZOE = new UUID(0, 4), LOU = new UUID(0, 5);
    private static final Map<UUID, String> PSEUDOS = Map.of(INES, "Inès", MAEL, "Maël", ZOE, "Zoé", LOU, "Lou");

    private static ResultatVote trio(long numero, List<UUID> interpretes, Map<UUID, Long> voix) {
        return new ResultatVote(new UUID(1, numero), MANCHE, numero, interpretes, 0, 0, null, voix);
    }

    @Test
    void classeLesInterpretesParVictoiresEtDonneUneVictoireAChaqueExAequo() {
        var lignes = ClassementParVictoires.classer(List.of(
                trio(1, List.of(INES, MAEL, ZOE), Map.of(ZOE, 3L, MAEL, 1L)),
                trio(2, List.of(MAEL, ZOE), Map.of(MAEL, 2L, ZOE, 2L)),
                trio(3, List.of(LOU, INES), Map.of()),
                new ResultatVote(new UUID(1, 4), MANCHE, 4, List.of(LOU), 2, 9, 45)), PSEUDOS);

        assertThat(lignes).extracting(l -> l.interprete().pseudo(), ClassementParVictoires.Ligne::nombreVictoires,
                ClassementParVictoires.Ligne::nombrePassages, ClassementParVictoires.Ligne::rang)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Zoé", 2L, 2L, 1L),
                        org.assertj.core.groups.Tuple.tuple("Maël", 1L, 2L, 2L),
                        org.assertj.core.groups.Tuple.tuple("Inès", 0L, 2L, null),
                        org.assertj.core.groups.Tuple.tuple("Lou", 0L, 1L, null));
    }

    @Test
    void sansPassageAPlusieursLeClassementEstVide() {
        assertThat(ClassementParVictoires.classer(List.of(
                new ResultatVote(new UUID(1, 1), MANCHE, 1, List.of(INES), 1, 4, 40)), PSEUDOS)).isEmpty();
    }
}
