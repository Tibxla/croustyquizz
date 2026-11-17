package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.ActionJoueur;
import fr.croustyquizz.manche.Acteur;
import java.time.Instant;
import java.util.UUID;

/** Inscription préparée hors verrou, puis revalidée par le domaine. */
public record InscrirePassage(Acteur acteur, Instant recueLe, UUID envoiId,
        FaitsInscription faits, String chansonId, ContenuKaraoke contenu) implements ActionJoueur {
    public InscrirePassage {
        if (!chansonId.equals(contenu.chanson().chansonId())) {
            throw new IllegalArgumentException("L’identifiant doit correspondre à la Chanson préparée.");
        }
    }
    @Override public UUID joueurId() { return acteur.identifiant(); }
}
