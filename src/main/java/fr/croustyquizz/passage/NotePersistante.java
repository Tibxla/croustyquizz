package fr.croustyquizz.passage;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "passage_note")
@IdClass(NotePersistante.Cle.class)
public class NotePersistante {
    @Id private UUID passageId;
    @Id private UUID joueurId;
    private short note;
    private Instant recueLe;

    protected NotePersistante() { }

    NotePersistante(UUID passageId, VoteRecu vote) {
        this.passageId = passageId;
        this.joueurId = vote.joueurId();
        this.note = (short) vote.note();
        this.recueLe = vote.recueLe();
    }

    UUID getPassageId() { return passageId; }

    VoteRecu restaurer() { return new VoteRecu(joueurId, note, recueLe); }

    public record Cle(UUID passageId, UUID joueurId) implements Serializable { }
}
