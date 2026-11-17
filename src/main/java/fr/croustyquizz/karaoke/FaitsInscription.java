package fr.croustyquizz.karaoke;

import java.util.UUID;

/** Identifiant et numéro proposés par le service, sans les consommer lors d’un refus. */
public record FaitsInscription(UUID passageId, long numeroPropose) {}
