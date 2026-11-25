package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.UUID;

/** Courant affiché et chronologie calculée à un instant donné. */
public record ProjectionCycle(Passage courant, boolean actif, String etat, UUID etapeId,
        Instant appeleLe, Instant echeanceAppelLe, Long delaiAppelRestantMs, Instant demarreLe, Instant finPrevueLe,
        Long positionChronologiqueMs, long tempsPauseMs, Instant clotureLe, Instant prochainAppelPrevuLe,
        Instant calculeLe) {}
