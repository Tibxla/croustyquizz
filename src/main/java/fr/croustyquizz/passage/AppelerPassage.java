package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.CommandeDeManche;
import java.time.Instant;
import java.util.UUID;

public record AppelerPassage(Acteur acteur, Instant recueLe, UUID envoiId, CiblePassage cible) implements CommandeDeManche {}
