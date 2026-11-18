package fr.croustyquizz.passage;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

/** Association ordonnée durable de la File. */
@Entity
@Table(name = "passage_interprete")
@IdClass(InterpretePersistant.Cle.class)
public class InterpretePersistant {
    @Id private UUID passageId;
    private UUID joueurId;
    @Id private int ordre;
    protected InterpretePersistant() { }
    public InterpretePersistant(UUID passageId, UUID joueurId, int ordre) {
        this.passageId = passageId;
        this.joueurId = joueurId;
        this.ordre = ordre;
    }
    public UUID getPassageId() { return passageId; }
    public UUID getJoueurId() { return joueurId; }
    public int getOrdre() { return ordre; }
    public record Cle(UUID passageId, int ordre) implements Serializable { }
}
