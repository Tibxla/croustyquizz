package fr.croustyquizz.passage;

import java.util.UUID;

/** Présence et pseudo des Joueurs, indépendamment de leur arrivée dans la Manche. */
public interface ParticipantsDePassage {
    boolean estPresent(UUID soireeId, UUID joueurId);
    String pseudo(UUID soireeId, UUID joueurId);
}
