package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.UUID;

/** Motif, acteur éventuel et instant de sortie de la File. */
public record TraceTerminale(String motif, UUID acteurId, Instant instant) {
    public static final String FIN_PREVUE = "FIN_PREVUE";
    public static final String FIN_LECTURE = "FIN_LECTURE";
    public static final String PASSE_PAR_ANIMATEUR = "PASSE_PAR_ANIMATEUR";
    public static final String MANCHE_TERMINEE = "MANCHE_TERMINEE";

    boolean correspondA(EtatPassage terminal) {
        if (instant == null) { return false; }
        return switch (terminal) {
            case TERMINE -> acteurId == null && (FIN_PREVUE.equals(motif) || FIN_LECTURE.equals(motif));
            case SAUTE -> acteurId != null && PASSE_PAR_ANIMATEUR.equals(motif);
            case ANNULE -> acteurId != null && MANCHE_TERMINEE.equals(motif);
            default -> false;
        };
    }
}
