package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.CommandeDeManche;
import java.time.Instant;
import java.util.UUID;

/** Commande horodatée par le service, gardée par le domaine. */
public record DeplacerPassage(Acteur acteur, Instant recueLe, UUID envoiId, CiblePassage cible, long tour, CiblePassage avant) implements CommandeDeManche {
    public DeplacerPassage { if (tour <= 0) { throw new IllegalArgumentException("Le Tour doit être positif."); } }
}
