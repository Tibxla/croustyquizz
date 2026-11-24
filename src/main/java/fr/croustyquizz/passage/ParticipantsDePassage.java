package fr.croustyquizz.passage;

import java.util.Map;
import java.util.UUID;

/** Présence et pseudo des Joueurs, indépendamment de leur arrivée dans la Manche. */
public interface ParticipantsDePassage {
    /** Pseudo d'un Interprète qui n'est plus présent (départ ou Exclusion, sans les distinguer). */
    String JOUEUR_PARTI = "Joueur parti";
    boolean estPresent(UUID soireeId, UUID joueurId);
    /** Pseudos des Joueurs présents, chargés ensemble pour les projections d'une Soirée. */
    Map<UUID, String> pseudos(UUID soireeId);
}
