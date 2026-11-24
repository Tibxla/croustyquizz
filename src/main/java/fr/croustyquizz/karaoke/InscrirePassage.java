package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.ActionJoueur;
import fr.croustyquizz.manche.Acteur;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Inscription préparée hors verrou, puis revalidée par le domaine. {@code invitesPresents} dit si tous les
 * Joueurs invités (#66) sont présents, d’après le service sous le verrou de Soirée.
 */
public record InscrirePassage(Acteur acteur, Instant recueLe, UUID envoiId,
        FaitsInscription faits, String chansonId, ContenuKaraoke contenu,
        List<UUID> invitesIds, boolean invitesPresents) implements ActionJoueur {
    public InscrirePassage {
        if (!chansonId.equals(contenu.chanson().chansonId())) {
            throw new IllegalArgumentException("L’identifiant doit correspondre à la Chanson préparée.");
        }
        invitesIds = List.copyOf(invitesIds);
    }
    public InscrirePassage(Acteur acteur, Instant recueLe, UUID envoiId, FaitsInscription faits, String chansonId,
            ContenuKaraoke contenu) {
        this(acteur, recueLe, envoiId, faits, chansonId, contenu, List.of(), true);
    }
    @Override public UUID joueurId() { return acteur.identifiant(); }
}
