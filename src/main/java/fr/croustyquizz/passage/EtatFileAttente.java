package fr.croustyquizz.passage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Capture indépendante et persistable de toute la File, historique compris, et des invitations en cours. */
public record EtatFileAttente(UUID soireeId, UUID mancheId,
        long limitePassagesEnAttente, long tourOuvert,
        long prochainOrdreInscription, long prochaineCleOrdre,
        UUID passagePrioritaireId, List<Passage> passages,
        Map<UUID, Long> derniersToursConsommes, List<InvitationPassage> invitations) {
    /** Borne de la limite par Interprète, identique à {@code LimitePassages} et {@code ReglagesKaraoke} du contrat. */
    public static final long LIMITE_PASSAGES_MAX = 20;

    public EtatFileAttente {
        verifierLimite(limitePassagesEnAttente);
        passages = List.copyOf(passages);
        derniersToursConsommes = Map.copyOf(derniersToursConsommes);
        // L’ordre des invitations est celui de leur émission, conservé par la persistance.
        invitations = List.copyOf(invitations);
    }

    public EtatFileAttente(UUID soireeId, UUID mancheId, long limitePassagesEnAttente, long tourOuvert,
            long prochainOrdreInscription, long prochaineCleOrdre, UUID passagePrioritaireId,
            List<Passage> passages, Map<UUID, Long> derniersToursConsommes) {
        this(soireeId, mancheId, limitePassagesEnAttente, tourOuvert, prochainOrdreInscription,
                prochaineCleOrdre, passagePrioritaireId, passages, derniersToursConsommes, List.of());
    }

    public static void verifierLimite(long limite) {
        if (limite <= 0 || limite > LIMITE_PASSAGES_MAX) {
            throw new IllegalArgumentException("La limite doit être comprise entre 1 et " + LIMITE_PASSAGES_MAX + ".");
        }
    }
}
