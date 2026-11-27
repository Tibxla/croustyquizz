package fr.croustyquizz.passage;

import fr.croustyquizz.manche.ActionJoueur;
import fr.croustyquizz.manche.Acteur;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Vote du public d’un Passage de Freestyle à plusieurs : le Joueur désigne un seul Interprète (#74). */
public record DesignerMeilleurInterprete(Acteur acteur, Instant recueLe, UUID envoiId, CiblePassage cible,
        UUID interpreteId) implements ActionJoueur {
    public DesignerMeilleurInterprete {
        Objects.requireNonNull(cible);
        Objects.requireNonNull(interpreteId);
    }

    @Override public UUID joueurId() { return acteur.identifiant(); }
}
