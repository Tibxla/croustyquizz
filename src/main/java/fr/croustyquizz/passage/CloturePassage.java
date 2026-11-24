package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.UUID;

/** Fait émis après la clôture du courant. */
public record CloturePassage(UUID passageId, UUID etapeId, EtatPassage etat, String motif, Instant instant) {}
