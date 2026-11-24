package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.UUID;

public record ClotureVote(UUID passageId, long nombreVotes, long sommeNotes, Instant clotureLe) { }
