package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.UUID;

/** Reçu figé du réglage de limite ou de priorité, avec compteur après décision. */
public record AccuseFileAttente(UUID envoiId, UUID soireeId, UUID mancheId,
        String action, Instant recueLe, CiblePassage passage) {}
