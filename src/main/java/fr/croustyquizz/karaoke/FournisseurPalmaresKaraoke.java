package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.passage.ManchesDePassage;
import fr.croustyquizz.tempsreel.FournisseurDeChampCommun;
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
        if (!concerne(destination)) { return null; }
        var karaoke = service.getObject();
        return karaoke.lecture(soireeId, () -> {
            var manches = depot.deLaSoiree(soireeId);
            if (ManchesDePassage.palmaresAAfficher(manches, ModeDeJeu.KARAOKE).isEmpty()) { return null; }
            var palmares = karaoke.composerPalmares(soireeId, manches);
            return Destination.CONSOLE.equals(destination) ? palmares
                    : palmares.stream().filter(ligne -> ligne.rang() != null).limit(3).toList();
        });
    }
}
