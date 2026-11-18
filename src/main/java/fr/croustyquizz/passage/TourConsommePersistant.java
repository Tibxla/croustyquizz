package fr.croustyquizz.passage;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

/** Association ordonnée durable de la File. */
@Entity
@Table(name = "passage_tour_consomme")
@IdClass(TourConsommePersistant.Cle.class)
public class TourConsommePersistant {
    @Id private UUID mancheId;
    @Id private UUID interpreteId;
    private long dernierTourConsomme;
    protected TourConsommePersistant() { }
    public TourConsommePersistant(UUID mancheId, UUID interpreteId, long dernierTourConsomme) {
        this.mancheId = mancheId;
        this.interpreteId = interpreteId;
        this.dernierTourConsomme = dernierTourConsomme;
    }
    public UUID getMancheId() { return mancheId; }
    public UUID getInterpreteId() { return interpreteId; }
    public long getDernierTourConsomme() { return dernierTourConsomme; }
    public record Cle(UUID mancheId, UUID interpreteId) implements Serializable { }
}
