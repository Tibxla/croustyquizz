package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.ActionJoueur;
import java.time.Instant;
import java.util.UUID;

public record ConfirmerPresencePassage(Acteur acteur, Instant recueLe, UUID envoiId,
        CiblePassage cible, UUID etapeId) implements ActionJoueur {
    @Override public UUID joueurId() { return acteur.identifiant(); }
}
