package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.UUID;

public record AccuseDesignation(UUID envoiId, UUID soireeId, UUID mancheId, UUID passageId, UUID interpreteId,
        Instant recueLe) { }
