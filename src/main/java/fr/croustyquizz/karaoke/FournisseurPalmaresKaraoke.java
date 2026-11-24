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

/** Affiche le Palmarès entre la fin d'une Manche de Karaoké et le démarrage de la suivante. */
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
        if (!concerne(destination)) { return null; }
        var karaoke = service.getObject();
        return karaoke.lecture(soireeId, () -> {
            var manches = depot.deLaSoiree(soireeId);
            if (!visible(manches)) { return null; }
            var palmares = karaoke.composerPalmares(soireeId, manches);
            return Destination.CONSOLE.equals(destination) ? palmares
                    : palmares.stream().filter(ligne -> ligne.rang() != null).limit(3).toList();
        });
    }

    private static boolean visible(List<ManchePersistante> manches) {
        if (manches.stream().anyMatch(m -> m.getEtat() == EtatManche.EN_COURS || m.getEtat() == EtatManche.EN_PAUSE)) {
            return false;
        }
        return manches.stream().filter(m -> m.getEtat() == EtatManche.TERMINEE).max(ORDRE_DES_FINS)
                .map(m -> m.getMode() == ModeDeJeu.KARAOKE).orElse(false);
    }
}
