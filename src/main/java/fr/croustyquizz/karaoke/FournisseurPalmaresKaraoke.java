package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.passage.ManchesDePassage;
import fr.croustyquizz.tempsreel.FournisseurDeChampCommun;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;

/** Affiche le Palmarès entre la fin d'une Manche de Karaoké et le démarrage de la suivante. */
public final class FournisseurPalmaresKaraoke implements FournisseurDeChampCommun {
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
                    return ManchesDePassage.palmaresAAfficher(manches, ModeDeJeu.KARAOKE).isPresent()
                            ? karaoke.composerPalmares(soireeId, manches) : null;
                });
                fait = true;
            }
            if (palmares == null) { return null; }
            return Destination.CONSOLE.equals(destination) ? palmares
                    : palmares.stream().filter(ligne -> ligne.rang() != null).limit(3).toList();
        }
    }
}
