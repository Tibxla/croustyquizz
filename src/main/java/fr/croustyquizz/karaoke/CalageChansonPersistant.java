package fr.croustyquizz.karaoke;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "karaoke_calage_chanson")
public class CalageChansonPersistant {
    @Embeddable
    public record Cle(String chansonId, long versionSourceId, String fichierNom,
            long fichierTailleOctets) implements Serializable { }

    @EmbeddedId private Cle cle;
    private long decalageMs;
    private long numeroModification;
    private Instant modifieLe;

    protected CalageChansonPersistant() { }

    long getDecalageMs() { return decalageMs; }
}
