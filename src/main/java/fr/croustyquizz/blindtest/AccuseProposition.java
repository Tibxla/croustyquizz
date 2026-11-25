package fr.croustyquizz.blindtest;

import java.time.Instant;
import java.util.UUID;

/** Accusé d'une Proposition de titre validée, conforme au schéma {@code AccuseProposition} du contrat. */
public record AccuseProposition(UUID envoiId, UUID etapeId, Instant recueLe, VerdictChamp titre,
		VerdictChamp artiste, int pointsGagnes) { }
