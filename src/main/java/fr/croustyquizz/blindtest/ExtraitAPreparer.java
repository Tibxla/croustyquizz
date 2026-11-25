package fr.croustyquizz.blindtest;

import java.util.UUID;

/** Morceau en préparation dont le service doit demander le lien d'extrait, hors du verrou de la Soirée. */
public record ExtraitAPreparer(UUID etapeId, long idDeezer) { }
