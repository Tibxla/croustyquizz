package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.UUID;

public record ProjectionVote(UUID passageId, boolean ouvert, long delaiRestantMs, Instant calculeLe, Instant clotureLe,
        long nombreVotes, long sommeNotes, Integer moyenneDixiemes, boolean suivantDemarre) { }
