package fr.croustyquizz.karaoke;

import java.time.Instant;
import java.util.UUID;

public record AccuseLectureChanson(UUID envoiId, UUID soireeId, UUID mancheId,
        UUID lecteurId, UUID passageId, UUID lectureId, String action, Instant recueLe) {}
