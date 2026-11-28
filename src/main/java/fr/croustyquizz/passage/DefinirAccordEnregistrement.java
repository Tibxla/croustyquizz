package fr.croustyquizz.passage;

import fr.croustyquizz.manche.ActionJoueur;
import java.time.Instant;
import java.util.UUID;

/** Le choix d’un Interprète et les faits relus sous le verrou de la Soirée. */
public record DefinirAccordEnregistrement(UUID joueurId, Instant recueLe, UUID envoiId, CiblePassage cible,
        boolean accord, boolean accordPrecedent, boolean autorise) implements ActionJoueur { }
