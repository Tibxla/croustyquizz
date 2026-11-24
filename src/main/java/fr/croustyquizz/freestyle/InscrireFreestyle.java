package fr.croustyquizz.freestyle;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.ActionJoueur;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Le service fournit les fiches de la Soirée et les mots de départ du thème choisi pour la Manche. */
public record InscrireFreestyle(Acteur acteur, Instant recueLe, UUID envoiId, UUID passageId, long numeroPropose,
        UUID instrumentaleId, List<Instrumentale> instrumentalesDisponibles, List<String> motsDuTheme) implements ActionJoueur {
    public InscrireFreestyle {
        Objects.requireNonNull(passageId);
        instrumentalesDisponibles = List.copyOf(instrumentalesDisponibles);
        motsDuTheme = List.copyOf(motsDuTheme);
        if (numeroPropose <= 0) { throw new IllegalArgumentException("Le numéro du Passage doit être positif."); }
    }

    @Override public UUID joueurId() { return acteur.identifiant(); }
}
