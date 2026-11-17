package fr.croustyquizz.passage;

import java.time.Instant;

/** Durée indicative à l’instant du calcul, sans événement périodique. */
public record AttenteEstimee(Instant calculeeLe, EtatAttente etat, Long dureeMs) {}
