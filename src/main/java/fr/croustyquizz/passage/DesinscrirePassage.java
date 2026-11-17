package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.ActionJoueur;
import java.time.Instant;
import java.util.UUID;

/** Commande horodatée par le service, gardée par le domaine. */
public record DesinscrirePassage(Acteur acteur, Instant recueLe, UUID envoiId, CiblePassage cible) implements ActionJoueur {
    @Override public UUID joueurId() { return acteur.identifiant(); }
}
