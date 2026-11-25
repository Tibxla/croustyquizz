package fr.croustyquizz.blindtest;

import java.time.Instant;
import java.util.UUID;

/** Reçu d'un Buzz qui a donné la main, conforme au schéma {@code Buzz} du contrat. */
public record AccuseBuzz(UUID envoiId, UUID joueurId, String pseudo, Instant recuLe, int rang) { }
