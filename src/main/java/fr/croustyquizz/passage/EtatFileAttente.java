package fr.croustyquizz.passage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Capture indépendante et persistable de toute la File, historique compris. */
public record EtatFileAttente(UUID soireeId, UUID mancheId,
        long limitePassagesEnAttente, long tourOuvert,
        long prochainOrdreInscription, long prochaineCleOrdre,
        UUID passagePrioritaireId, List<Passage> passages,
        Map<UUID, Long> derniersToursConsommes) {
    public EtatFileAttente {
        verifierLimite(limitePassagesEnAttente);
        passages = List.copyOf(passages);
        derniersToursConsommes = Map.copyOf(derniersToursConsommes);
    }

    public static void verifierLimite(long limite) {
        if (limite <= 0 || limite > 9007199254740991L) {
            throw new IllegalArgumentException("La limite doit être un entier positif représentable.");
        }
    }
}
