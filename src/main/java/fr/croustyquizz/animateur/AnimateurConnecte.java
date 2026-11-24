package fr.croustyquizz.animateur;

import java.util.UUID;

/** Identité de session pour une opération qui ne désigne aucune Soirée. */
public record AnimateurConnecte(UUID animateurId, UUID lieuId) { }
