package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.CommandeDeManche;
import java.time.Instant;
import java.util.UUID;

/** Commande horodatée par le service, gardée par le domaine. */
public record RetirerPassage(Acteur acteur, Instant recueLe, UUID envoiId, CiblePassage cible) implements CommandeDeManche {
}
