package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.UUID;

/** Motif, acteur éventuel et instant de sortie de la File. */
public record TraceTerminale(String motif, UUID acteurId, Instant instant) {}
