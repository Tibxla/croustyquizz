package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Note personnelle et instant de réception par le serveur. */
public record VoteRecu(UUID joueurId, int note, Instant recueLe) {
    public static final int NOTE_MAXIMALE = 5;

    public VoteRecu {
        Objects.requireNonNull(joueurId);
        Objects.requireNonNull(recueLe);
        verifierNote(note);
    }

    static void verifierNote(int note) {
        if (note < 1 || note > NOTE_MAXIMALE) { throw new IllegalArgumentException("La note doit être comprise entre 1 et 5."); }
    }
}
