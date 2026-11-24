package fr.croustyquizz.passage;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Classe les Passages selon leurs votes clos, sur la moyenne exacte. */
public final class ClassementParVote {
    private static final Comparator<ResultatVote> PAR_NUMERO = Comparator.comparingLong(ResultatVote::numero);
    private static final Comparator<ResultatVote> ORDRE = ClassementParVote::comparerMoyennes;

    private ClassementParVote() { }

    public static List<RangPassage> classer(List<ResultatVote> resultats) {
        var parNombreDeVotes = Comparator.comparingLong(ResultatVote::nombreVotes).reversed();
        var notes = resultats.stream().filter(vote -> vote.nombreVotes() > 0)
                .sorted(ORDRE.thenComparing(parNombreDeVotes).thenComparing(PAR_NUMERO)).toList();
        List<RangPassage> classement = new ArrayList<>(resultats.size());
        for (var note : notes) {
            classement.add(new RangPassage(note, classement.size() + 1L));
        }
        resultats.stream().filter(vote -> vote.nombreVotes() == 0).sorted(PAR_NUMERO)
                .forEach(vote -> classement.add(new RangPassage(vote, null)));
        return List.copyOf(classement);
    }

    private static int comparerMoyennes(ResultatVote a, ResultatVote b) {
        var produitA = BigInteger.valueOf(a.sommeNotes()).multiply(BigInteger.valueOf(b.nombreVotes()));
        var produitB = BigInteger.valueOf(b.sommeNotes()).multiply(BigInteger.valueOf(a.nombreVotes()));
        return produitB.compareTo(produitA);
    }
}
