package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Avis d’un Joueur et instant de réception par le serveur : une note de 1 à 5, ou, pour un Passage de Freestyle
 * à plusieurs (#74), l’Interprète qu’il désigne. Exactement l’un des deux est renseigné.
 */
public record VoteRecu(UUID joueurId, Integer note, UUID interpreteDesigneId, Instant recueLe) {
    public static final int NOTE_MAXIMALE = 5;

    public VoteRecu {
        Objects.requireNonNull(joueurId);
        Objects.requireNonNull(recueLe);
        if ((note == null) == (interpreteDesigneId == null)) {
            throw new IllegalArgumentException("Un vote porte soit une note, soit un Interprète désigné.");
        }
        if (note != null) { verifierNote(note); }
    }

    public VoteRecu(UUID joueurId, int note, Instant recueLe) { this(joueurId, note, null, recueLe); }

    public static VoteRecu designation(UUID joueurId, UUID interpreteId, Instant recueLe) {
        return new VoteRecu(joueurId, null, Objects.requireNonNull(interpreteId), recueLe);
    }

    static void verifierNote(int note) {
        if (note < 1 || note > NOTE_MAXIMALE) { throw new IllegalArgumentException("La note doit être comprise entre 1 et 5."); }
    }
}
