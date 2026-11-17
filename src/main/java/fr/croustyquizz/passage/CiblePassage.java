package fr.croustyquizz.passage;

import java.util.UUID;

/** Identité et compteur attendus du seul Passage visé. */
public record CiblePassage(UUID passageId, long numeroModification) {}
