package fr.croustyquizz.blindtest;

import java.time.Instant;
import java.util.UUID;

/** Reçu de l'arbitrage, conforme au schéma {@code AccuseArbitrage} du contrat. */
public record AccuseArbitrage(UUID envoiId, UUID etapeId, Instant recueLe, UUID propositionId, PartieDuMorceau partie,
		VerdictChamp verdict, boolean modifie) { }
