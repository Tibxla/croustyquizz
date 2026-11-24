package fr.croustyquizz.passage;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Chronologie du courant. L’état métier et son compteur restent dans la File. */
public record EtatCyclePassage(UUID passageId, UUID etapeId, Instant appeleLe, Instant echeanceAppelLe,
        Instant demarreLe, Duration dureeReference, Duration tempsPause, Instant pauseDepuis, Instant clotureLe) {
    public EtatCyclePassage {
        if (tempsPause == null || tempsPause.isNegative()
                || (passageId == null && (etapeId != null || appeleLe != null || echeanceAppelLe != null
                        || demarreLe != null || pauseDepuis != null || clotureLe != null || !tempsPause.isZero()))
                || (passageId != null && (etapeId == null || appeleLe == null || echeanceAppelLe == null))
                || ((demarreLe == null) != (dureeReference == null))
                || (dureeReference != null && !dureeReference.isPositive())
                || (clotureLe != null && pauseDepuis != null)) {
            throw new IllegalArgumentException("La chronologie du Passage est incohérente.");
        }
    }

    public static EtatCyclePassage vide() {
        return new EtatCyclePassage(null, null, null, null, null, null, Duration.ZERO, null, null);
    }

    Instant finPrevue() { return demarreLe.plus(dureeReference).plus(tempsPause); }
}
