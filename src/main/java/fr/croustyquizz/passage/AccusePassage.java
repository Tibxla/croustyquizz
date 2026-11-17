package fr.croustyquizz.passage;

import java.time.Instant;
import java.util.UUID;

/** Reçu figé de l’inscription, désinscription, retrait ou placement. */
public record AccusePassage(UUID envoiId, UUID soireeId, UUID mancheId,
        UUID passageId, long numeroModification, String action, Instant recueLe, EtatPassage etatApres) {}
