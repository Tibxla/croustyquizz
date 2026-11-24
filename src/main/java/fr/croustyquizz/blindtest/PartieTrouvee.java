package fr.croustyquizz.blindtest;

import java.util.UUID;

/** Partie d'un Morceau trouvée, montrée aussitôt à toutes les destinations (ADR 0009, point 10). */
public record PartieTrouvee(PartieDuMorceau partie, String valeur, UUID joueurId, String pseudo, int points) { }
