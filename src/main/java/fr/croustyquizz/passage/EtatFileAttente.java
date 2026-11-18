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
    /** Borne de la limite par Interprète, identique à {@code LimitePassages} et {@code ReglagesKaraoke} du contrat. */
    public static final long LIMITE_PASSAGES_MAX = 20;

    public EtatFileAttente {
        verifierLimite(limitePassagesEnAttente);
        passages = List.copyOf(passages);
        derniersToursConsommes = Map.copyOf(derniersToursConsommes);
    }

    public static void verifierLimite(long limite) {
        if (limite <= 0 || limite > LIMITE_PASSAGES_MAX) {
            throw new IllegalArgumentException("La limite doit être comprise entre 1 et " + LIMITE_PASSAGES_MAX + ".");
        }
    }
}
