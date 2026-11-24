package fr.croustyquizz.freestyle;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Couplets d’un Passage à plusieurs (#74), figés à « Je suis prêt ». L’ordre tiré au sort se répète tant que la
 * Grille compte des Couplets. {@code suivantAnnonce} passe à vrai une mesure avant le changement de Couplet.
 */
public record CoupletsDuPassage(int mesuresParCouplet, int nombreCouplets, List<UUID> ordre, int courant,
        boolean suivantAnnonce) {
    public CoupletsDuPassage {
        ordre = List.copyOf(ordre);
        if (ordre.size() < 2 || new HashSet<>(ordre).size() != ordre.size() || nombreCouplets < 1
                || courant < 1 || courant > nombreCouplets || (suivantAnnonce && courant == nombreCouplets)) {
            throw new IllegalArgumentException("Les Couplets du Passage sont incohérents.");
        }
    }

    static CoupletsDuPassage tirer(ReglagesFreestyle reglages, List<UUID> ordre) {
        return new CoupletsDuPassage(reglages.mesuresParCouplet(), reglages.nombreCouplets(), ordre, 1, false);
    }

    UUID interpreteDu(int numero) {
        Objects.checkIndex(numero - 1, nombreCouplets);
        return ordre.get((numero - 1) % ordre.size());
    }

    int premiereMesure(int numero) { return (numero - 1) * mesuresParCouplet + 1; }

    /** Temps de la Grille, compté depuis le départ du beat, du prochain événement ; -1 après le dernier changement. */
    int tempsDuProchainEvenement() {
        if (courant == nombreCouplets) { return -1; }
        int changement = GrilleDeRimes.TEMPS_PAR_MESURE * courant * mesuresParCouplet;
        return suivantAnnonce ? changement : changement - GrilleDeRimes.TEMPS_PAR_MESURE;
    }

    /** Annonce le suivant, ou fait commencer le Couplet annoncé. */
    CoupletsDuPassage avancer() {
        return suivantAnnonce ? new CoupletsDuPassage(mesuresParCouplet, nombreCouplets, ordre, courant + 1, false)
                : new CoupletsDuPassage(mesuresParCouplet, nombreCouplets, ordre, courant, true);
    }
}
