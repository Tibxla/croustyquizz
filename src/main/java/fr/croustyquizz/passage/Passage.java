package fr.croustyquizz.passage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.HashSet;
import java.util.UUID;

/** Passage immuable ; le contenu reste opaque au domaine partagé. */
public record Passage(UUID id, UUID soireeId, UUID mancheId,
        UUID inscripteurId, List<UUID> interpretes, String contenuId,
        Duration duree, long numero, long numeroModification,
        EtatPassage etat, Instant inscriptionLe, long ordreInscription,
        long tourNormal, long cleOrdre, Long tourConsomme, TraceTerminale traceTerminale) {
    static final int NOMBRE_MAX_INTERPRETES = 4;

    public Passage {
        interpretes = List.copyOf(interpretes);
        verifierInterpretes(inscripteurId, interpretes);
        if (duree.isZero() || duree.isNegative() || numero <= 0) {
            throw new IllegalArgumentException("La durée et le numéro doivent être positifs.");
        }
    }

    static void verifierInterpretes(UUID inscripteurId, List<UUID> interpretes) {
        if (interpretes.isEmpty() || interpretes.size() > NOMBRE_MAX_INTERPRETES
                || new HashSet<>(interpretes).size() != interpretes.size()) {
            throw new IllegalArgumentException("Un Passage exige un à quatre Interprètes distincts.");
        }
        // Le premier Interprète est l’inscrivant : les vues le désignent ainsi (#66).
        if (!interpretes.getFirst().equals(inscripteurId)) {
            throw new IllegalArgumentException("L’inscripteur doit être le premier Interprète du Passage.");
        }
    }
}
