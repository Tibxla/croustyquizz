package fr.croustyquizz.tempsreel;

import java.util.UUID;

/** État de la scène après publication d’une vue validée, sous le verrou de Soirée. */
public record CycleDeScene(UUID soireeId, boolean mancheActive, boolean soireeTerminee) { }
