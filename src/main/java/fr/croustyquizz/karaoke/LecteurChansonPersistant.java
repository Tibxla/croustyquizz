package fr.croustyquizz.karaoke;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "karaoke_lecteur_chanson")
public class LecteurChansonPersistant {
    @Id private UUID mancheId;
    private UUID lecteurId;

    protected LecteurChansonPersistant() { }

    LecteurChansonPersistant(UUID mancheId, UUID lecteurId) {
        this.mancheId = mancheId;
        this.lecteurId = lecteurId;
    }

    UUID getLecteurId() { return lecteurId; }
}
