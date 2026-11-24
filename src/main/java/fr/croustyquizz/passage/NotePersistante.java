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
    /** Nulle pour la désignation d’un Interprète (#74). */
    private Short note;
    private UUID interpreteDesigneId;
    private Instant recueLe;

    protected NotePersistante() { }

    NotePersistante(UUID passageId, VoteRecu vote) {
        this.passageId = passageId;
        this.joueurId = vote.joueurId();
        this.note = vote.note() == null ? null : vote.note().shortValue();
        this.interpreteDesigneId = vote.interpreteDesigneId();
        this.recueLe = vote.recueLe();
    }

    UUID getPassageId() { return passageId; }

    VoteRecu restaurer() {
        return new VoteRecu(joueurId, note == null ? null : note.intValue(), interpreteDesigneId, recueLe);
    }

    public record Cle(UUID passageId, UUID joueurId) implements Serializable { }
}
