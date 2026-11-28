package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.tempsreel.FournisseurDeChampCommun;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;

/** Affiche le Palmarès entre la fin d'une Manche de Karaoké et le démarrage de la suivante, complet pour la salle et la Console. */
public final class FournisseurPalmaresKaraoke implements FournisseurDeChampCommun {
    private static final Comparator<ManchePersistante> ORDRE_DES_FINS = Comparator
            .comparing(ManchePersistante::getModifieLe).thenComparing(ManchePersistante::getMancheId);
    private final DepotDeManches depot;
    private final ObjectProvider<ServiceKaraoke> service;

    public FournisseurPalmaresKaraoke(DepotDeManches depot, ObjectProvider<ServiceKaraoke> service) {
        this.depot = depot;
        this.service = service;
    }

    @Override public String champ() { return "palmaresKaraoke"; }

    @Override public boolean concerne(Destination destination) {
        return Destination.SALLE.equals(destination) || Destination.CONSOLE.equals(destination);
    }

    @Override public Object fournir(UUID soireeId, Destination destination, Map<String, Object> partieCommune) {
        return preparer(soireeId).fournir(destination, partieCommune);
    }

    /** Calcule la liste au plus une fois par assemblage, partagée par la salle et la Console. */
    @Override public ChampPrepare preparer(UUID soireeId) {
        var calcul = new Calcul(soireeId);
        return calcul::pour;
    }

    private final class Calcul {
        private final UUID soireeId;
        private List<VuesKaraoke.LignePalmaresKaraoke> palmares;
        private boolean fait;

        Calcul(UUID soireeId) { this.soireeId = soireeId; }

        Object pour(Destination destination, Map<String, Object> partieCommune) {
            // Une Manche en cours ou en pause masque le Palmarès : un Buzz n'a pas à relire les Manches.
            if (!concerne(destination) || partieCommune.get("mancheActive") != null) { return null; }
            if (!fait) {
                var karaoke = service.getObject();
                palmares = karaoke.lecture(soireeId, () -> {
                    var manches = depot.deLaSoiree(soireeId);
                    return visible(manches) ? karaoke.composerPalmares(soireeId, manches) : null;
                });
                fait = true;
            }
            // La salle reçoit la même liste que la Console : elle en affiche le haut et compte le reste (#236).
            return palmares;
        }
    }

    private static boolean visible(List<ManchePersistante> manches) {
        if (manches.stream().anyMatch(m -> m.getEtat() == EtatManche.EN_COURS || m.getEtat() == EtatManche.EN_PAUSE)) {
            return false;
        }
        return manches.stream().filter(m -> m.getEtat() == EtatManche.TERMINEE).max(ORDRE_DES_FINS)
                .map(m -> m.getMode() == ModeDeJeu.KARAOKE).orElse(false);
    }
}
