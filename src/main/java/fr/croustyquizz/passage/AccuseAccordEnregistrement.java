package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.UUID;

public record AccuseAccordEnregistrement(UUID envoiId, UUID soireeId, UUID mancheId, UUID passageId,
        boolean accord, Instant recueLe) { }
