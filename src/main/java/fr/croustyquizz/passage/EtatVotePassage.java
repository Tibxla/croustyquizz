package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EtatVotePassage(UUID passageId, Instant ouvertLe, Instant echeanceLe, Instant pauseDepuis,
        Instant clotureLe, List<VoteRecu> votes, boolean suivantDemarre) {
    public EtatVotePassage {
        Objects.requireNonNull(passageId);
        Objects.requireNonNull(ouvertLe);
        Objects.requireNonNull(echeanceLe);
        votes = List.copyOf(votes);
        if (votes.stream().map(VoteRecu::joueurId).distinct().count() != votes.size()) {
            throw new IllegalArgumentException("Une seule note par Joueur est permise.");
        }
        if (clotureLe != null && pauseDepuis != null) {
            throw new IllegalArgumentException("Un vote clos ne peut pas être en pause.");
        }
    }

    public EtatVotePassage(UUID passageId, Instant ouvertLe, Instant echeanceLe, Instant pauseDepuis,
            Instant clotureLe, List<VoteRecu> votes) {
        this(passageId, ouvertLe, echeanceLe, pauseDepuis, clotureLe, votes, false);
    }

    public boolean ouvert() { return clotureLe == null; }
}
