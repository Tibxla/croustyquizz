package fr.croustyquizz.passage;

import java.util.UUID;

/** Présence et pseudo des Joueurs, indépendamment de leur arrivée dans la Manche. */
public interface ParticipantsDePassage {
    /** Pseudo d'un Interprète qui n'est plus présent (départ ou Exclusion, sans les distinguer). */
    String JOUEUR_PARTI = "Joueur parti";
    boolean estPresent(UUID soireeId, UUID joueurId);
    String pseudo(UUID soireeId, UUID joueurId);
}
