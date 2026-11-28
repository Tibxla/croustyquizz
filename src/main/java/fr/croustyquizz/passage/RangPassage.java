package fr.croustyquizz.passage;

/** Le rang est nul pour un Passage qui n'a reçu aucun vote. */
public record RangPassage(ResultatVote resultat, Long rang) { }
