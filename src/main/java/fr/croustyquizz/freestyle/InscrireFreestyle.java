package fr.croustyquizz.freestyle;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.ActionJoueur;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Le service fournit les fiches de la Soirée, les mots de départ du thème choisi pour la Manche et, pour les
 * Invitations (#74), la présence de tous les invités lue sous le verrou de Soirée.
 */
public record InscrireFreestyle(Acteur acteur, Instant recueLe, UUID envoiId, UUID passageId, long numeroPropose,
        UUID instrumentaleId, List<Instrumentale> instrumentalesDisponibles, List<String> motsDuTheme,
        List<UUID> invitesIds, boolean invitesPresents) implements ActionJoueur {
    public InscrireFreestyle {
        Objects.requireNonNull(passageId);
        instrumentalesDisponibles = List.copyOf(instrumentalesDisponibles);
        motsDuTheme = List.copyOf(motsDuTheme);
        invitesIds = List.copyOf(invitesIds);
        if (numeroPropose <= 0) { throw new IllegalArgumentException("Le numéro du Passage doit être positif."); }
    }

    public InscrireFreestyle(Acteur acteur, Instant recueLe, UUID envoiId, UUID passageId, long numeroPropose,
            UUID instrumentaleId, List<Instrumentale> instrumentalesDisponibles, List<String> motsDuTheme) {
        this(acteur, recueLe, envoiId, passageId, numeroPropose, instrumentaleId, instrumentalesDisponibles, motsDuTheme,
                List.of(), true);
    }

    @Override public UUID joueurId() { return acteur.identifiant(); }
}
