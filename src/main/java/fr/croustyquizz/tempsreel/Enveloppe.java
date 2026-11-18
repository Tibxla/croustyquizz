package fr.croustyquizz.tempsreel;

import java.time.Instant;
import java.util.UUID;

/** Vue complète publiée avec une Révision contiguë propre à sa destination. */
public record Enveloppe(String type, UUID soireeId, long revision, Instant emisLe, Evenement donnees) {
}
