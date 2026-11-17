package fr.croustyquizz.passage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Faits figés par le service avant l’inscription. */
public record NouveauPassage(UUID id, UUID soireeId, UUID mancheId,
        UUID inscripteurId, List<UUID> interpretes, String contenuId,
        Duration duree, long numero, Instant recueLe) {
    private static final int NOMBRE_INTERPRETES = 1;

    public NouveauPassage {
        interpretes = List.copyOf(interpretes);
        if (interpretes.size() != NOMBRE_INTERPRETES) {
            throw new IllegalArgumentException("Cette tranche exige exactement un Interprète.");
        }
        if (duree.isZero() || duree.isNegative() || numero <= 0) {
            throw new IllegalArgumentException("La durée et le numéro doivent être positifs.");
        }
    }
}
