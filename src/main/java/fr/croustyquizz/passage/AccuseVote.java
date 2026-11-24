package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.UUID;

public record AccuseVote(UUID envoiId, UUID soireeId, UUID mancheId, UUID passageId, int note, Instant recueLe) { }
