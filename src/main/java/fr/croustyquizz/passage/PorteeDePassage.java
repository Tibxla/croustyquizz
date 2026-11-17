package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Acteur;
import java.util.UUID;

/** Accès à une Manche et activité de sa Soirée. */
public interface PorteeDePassage {
    void verifier(Acteur acteur, UUID soireeId, UUID mancheId);
    boolean estActive(UUID soireeId, UUID mancheId);
}
