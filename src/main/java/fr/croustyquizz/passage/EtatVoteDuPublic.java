package fr.croustyquizz.passage;

import java.util.List;

public record EtatVoteDuPublic(List<EtatVotePassage> votes) {
    public EtatVoteDuPublic {
        votes = List.copyOf(votes);
        if (votes.stream().map(EtatVotePassage::passageId).distinct().count() != votes.size()) {
            throw new IllegalArgumentException("Un seul vote par Passage est permis.");
        }
    }

    public static EtatVoteDuPublic vide() { return new EtatVoteDuPublic(List.of()); }
}
