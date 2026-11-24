package fr.croustyquizz.karaoke;

import java.time.Instant;
import java.util.UUID;

public record AccuseCalage(UUID envoiId, UUID soireeId, UUID mancheId, UUID passageId, UUID lectureId,
        String action, Instant recueLe, UUID demandeId, long numeroModificationCalage) {}
