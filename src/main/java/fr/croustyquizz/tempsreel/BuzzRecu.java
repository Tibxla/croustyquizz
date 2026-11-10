package fr.croustyquizz.tempsreel;

import java.time.Instant;

/** Accusé HTTP d’un Buzz, inclus dans la vue complète de salle. */
public record BuzzRecu(String pseudo, Instant recuLe) {
}
