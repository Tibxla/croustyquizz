package fr.croustyquizz.passage;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "passage_cycle")
public class CyclePassagePersistant {
    @Id private UUID mancheId;
    private UUID passageId;
    private UUID etapeId;
    private Instant appeleLe;
    private Instant echeanceAppelLe;
    private Instant demarreLe;
    private Long dureeReferenceMs;
    private long tempsPauseMs;
    private Instant pauseDepuis;
    private Instant clotureLe;

    protected CyclePassagePersistant() { }

    CyclePassagePersistant(UUID mancheId, EtatCyclePassage cycle) {
        this.mancheId = mancheId;
        passageId = cycle.passageId();
        etapeId = cycle.etapeId();
        appeleLe = cycle.appeleLe();
        echeanceAppelLe = cycle.echeanceAppelLe();
        demarreLe = cycle.demarreLe();
        if (cycle.dureeReference() != null) { dureeReferenceMs = cycle.dureeReference().toMillis(); }
        tempsPauseMs = cycle.tempsPause().toMillis();
        pauseDepuis = cycle.pauseDepuis();
        clotureLe = cycle.clotureLe();
    }

    EtatCyclePassage restaurer() {
        return new EtatCyclePassage(passageId, etapeId, appeleLe, echeanceAppelLe, demarreLe,
                dureeReferenceMs == null ? null : Duration.ofMillis(dureeReferenceMs),
                Duration.ofMillis(tempsPauseMs), pauseDepuis, clotureLe);
    }
}
