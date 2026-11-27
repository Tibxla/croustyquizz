package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.ActionJoueur;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Acceptation ou refus de son Invitation par le Joueur invité (#138), horodaté par le service. */
public record RepondreInvitation(Acteur acteur, Instant recueLe, UUID envoiId, CiblePassage cible, boolean accepte)
        implements ActionJoueur {
    public RepondreInvitation { Objects.requireNonNull(cible); }
    @Override public UUID joueurId() { return acteur.identifiant(); }
    public String action() { return accepte ? "ACCEPTER" : "REFUSER"; }
}
