package fr.croustyquizz.freestyle;

import fr.croustyquizz.manche.EtatManche;

/** Capture complète et immuable pour les projections et le socle de Manche. */
public record InstantaneFreestyle(EtatManche etatManche, ReglagesFreestyle reglages, EtatFreestyle etat,
        boolean pilotageAutomatique) { }
