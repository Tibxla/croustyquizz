package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.UUID;

public record AccuseCyclePassage(UUID envoiId, UUID soireeId, UUID mancheId,
        UUID passageId, UUID etapeId, long numeroModification, String action, Instant recueLe, EtatPassage etatApres) {}
