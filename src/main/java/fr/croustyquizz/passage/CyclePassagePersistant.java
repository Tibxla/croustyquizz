package fr.croustyquizz.passage;

import static java.time.temporal.ChronoUnit.MICROS;

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
    // La pause peut durer moins d’une milliseconde. Seules les projections la convertissent en ms.
    private Long dureeReferenceSecondes;
    private Integer dureeReferenceNanos;
    private long tempsPauseSecondes;
    private int tempsPauseNanos;
    private Instant pauseDepuis;
    private Instant clotureLe;

    protected CyclePassagePersistant() { }

    CyclePassagePersistant(UUID mancheId, EtatCyclePassage cycle) {
        this.mancheId = mancheId;
        passageId = cycle.passageId();
        etapeId = cycle.etapeId();
        if (cycle.appeleLe() != null) { appeleLe = cycle.appeleLe().truncatedTo(MICROS); }
        if (cycle.echeanceAppelLe() != null) { echeanceAppelLe = cycle.echeanceAppelLe().truncatedTo(MICROS); }
        if (cycle.demarreLe() != null) { demarreLe = cycle.demarreLe().truncatedTo(MICROS); }
        if (cycle.dureeReference() != null) {
            dureeReferenceSecondes = cycle.dureeReference().getSeconds();
            dureeReferenceNanos = cycle.dureeReference().getNano();
        }
        tempsPauseSecondes = cycle.tempsPause().getSeconds();
        tempsPauseNanos = cycle.tempsPause().getNano();
        if (cycle.pauseDepuis() != null) { pauseDepuis = cycle.pauseDepuis().truncatedTo(MICROS); }
        if (cycle.clotureLe() != null) { clotureLe = cycle.clotureLe().truncatedTo(MICROS); }
    }

    EtatCyclePassage restaurer() {
        return new EtatCyclePassage(passageId, etapeId, appeleLe, echeanceAppelLe, demarreLe,
                dureeReferenceSecondes == null ? null : Duration.ofSeconds(dureeReferenceSecondes, dureeReferenceNanos),
                Duration.ofSeconds(tempsPauseSecondes, tempsPauseNanos), pauseDepuis, clotureLe);
    }
}
