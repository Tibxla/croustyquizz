package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.UUID;

/** Motif, acteur éventuel et instant de sortie de la File. */
public record TraceTerminale(String motif, UUID acteurId, Instant instant) {
    public static final String FIN_PREVUE = "FIN_PREVUE";
    public static final String FIN_LECTURE = "FIN_LECTURE";
    public static final String PASSE_PAR_ANIMATEUR = "PASSE_PAR_ANIMATEUR";
    public static final String MANCHE_TERMINEE = "MANCHE_TERMINEE";
}
