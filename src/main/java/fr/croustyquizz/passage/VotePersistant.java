package fr.croustyquizz.passage;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "passage_vote")
public class VotePersistant {
    @Id private UUID passageId;
    private UUID mancheId;
    private Instant ouvertLe;
    private Instant echeanceLe;
    private Instant pauseDepuis;
    private Instant clotureLe;
    private boolean suivantDemarre;

    protected VotePersistant() { }

    VotePersistant(UUID mancheId, EtatVotePassage vote) {
        this.passageId = vote.passageId();
        this.mancheId = mancheId;
        this.ouvertLe = vote.ouvertLe();
        this.echeanceLe = vote.echeanceLe();
        this.pauseDepuis = vote.pauseDepuis();
        this.clotureLe = vote.clotureLe();
        this.suivantDemarre = vote.suivantDemarre();
    }

    UUID getPassageId() { return passageId; }

    EtatVotePassage restaurer(List<VoteRecu> notes) {
        return new EtatVotePassage(passageId, ouvertLe, echeanceLe, pauseDepuis, clotureLe, notes, suivantDemarre);
    }
}
