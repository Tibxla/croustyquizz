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
    public Passage {
        interpretes = List.copyOf(interpretes);
        if (interpretes.isEmpty() || interpretes.size() > 4
                || new HashSet<>(interpretes).size() != interpretes.size()) {
            throw new IllegalArgumentException("Un Passage exige un à quatre Interprètes distincts.");
        }
        if (duree.isZero() || duree.isNegative() || numero <= 0) {
            throw new IllegalArgumentException("La durée et le numéro doivent être positifs.");
        }
    }
}
