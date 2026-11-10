package fr.croustyquizz.tempsreel;

import java.time.Instant;
import java.util.UUID;

/** Capture complète et sa Révision, prises au même point cohérent. */
public record VueSynchronisee(long revision, Instant heureServeur, UUID abonnementId, Evenement vue) {
}
