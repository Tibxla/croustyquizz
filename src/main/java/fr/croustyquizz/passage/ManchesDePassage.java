package fr.croustyquizz.passage;

import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.ModeDeJeu;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Choix des Manches communs aux Modes à Passages : la Manche active et celle dont on montre le Palmarès. */
public final class ManchesDePassage {
    private static final Comparator<ManchePersistante> ORDRE_DES_FINS = Comparator
            .comparing(ManchePersistante::getModifieLe).thenComparing(ManchePersistante::getMancheId);

    private ManchesDePassage() { }

    public static boolean estActive(ManchePersistante manche) {
        return manche.getEtat() == EtatManche.EN_COURS || manche.getEtat() == EtatManche.EN_PAUSE;
    }

    public static Optional<ManchePersistante> active(List<ManchePersistante> manches, ModeDeJeu mode) {
        return manches.stream().filter(manche -> manche.getMode() == mode && estActive(manche)).findFirst();
    }

    /** Dernière Manche terminée de la Soirée, si elle est de ce Mode et qu’aucune Manche n’est active. */
    public static Optional<ManchePersistante> palmaresAAfficher(List<ManchePersistante> manches, ModeDeJeu mode) {
        if (manches.stream().anyMatch(ManchesDePassage::estActive)) { return Optional.empty(); }
        return manches.stream().filter(manche -> manche.getEtat() == EtatManche.TERMINEE).max(ORDRE_DES_FINS)
                .filter(manche -> manche.getMode() == mode);
    }
}
