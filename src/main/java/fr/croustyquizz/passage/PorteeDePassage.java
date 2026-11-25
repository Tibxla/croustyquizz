package fr.croustyquizz.passage;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.ModeDeJeu;
import java.util.UUID;

/**
 * Accès à une Manche et activité de sa Soirée.
 * Le Karaoké et le Freestyle partagent cette portée.
 */
public interface PorteeDePassage {
    static boolean accepte(ModeDeJeu mode) {
        return mode == ModeDeJeu.KARAOKE || mode == ModeDeJeu.FREESTYLE;
    }

    void verifier(Acteur acteur, UUID soireeId, UUID mancheId);
    boolean estActive(UUID soireeId, UUID mancheId);
}
