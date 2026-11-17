package fr.croustyquizz.manche;

import java.time.Instant;
import java.util.UUID;

/** Reçu immuable du pilotage, conforme au contrat HTTP. */
public record AccuseCommande(UUID envoiId, Instant recueLe, UUID soireeId) { }
