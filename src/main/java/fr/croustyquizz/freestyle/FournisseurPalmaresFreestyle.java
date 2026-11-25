package fr.croustyquizz.freestyle;

import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.passage.ClassementParVote;
import fr.croustyquizz.passage.ManchesDePassage;
import fr.croustyquizz.passage.ParticipantsDePassage;
import fr.croustyquizz.passage.PersistancePassageJpa;
import fr.croustyquizz.tempsreel.FournisseurDeChampCommun;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
final class FournisseurPalmaresFreestyle implements FournisseurDeChampCommun {
    private final DepotDeManches depot;
    private final ObjectProvider<ServiceFreestyle> services;
    private final PersistanceFreestyleJpa persistance;
    private final PersistancePassageJpa passages;
    private final ParticipantsDePassage participants;

    FournisseurPalmaresFreestyle(DepotDeManches depot, ObjectProvider<ServiceFreestyle> services,
            PersistanceFreestyleJpa persistance, PersistancePassageJpa passages, ParticipantsDePassage participants) {
        this.depot = depot;
        this.services = services;
        this.persistance = persistance;
        this.passages = passages;
        this.participants = participants;
    }

    @Override public String champ() { return "palmaresFreestyle"; }

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
        private List<VuesFreestyle.LignePalmares> palmares;
        private boolean fait;

        Calcul(UUID soireeId) { this.soireeId = soireeId; }

        Object pour(Destination destination, Map<String, Object> partieCommune) {
            // Une Manche en cours ou en pause masque le Palmarès : inutile de relire les Manches.
            if (!concerne(destination) || partieCommune.get("mancheActive") != null) { return null; }
            if (!fait) {
                palmares = services.getObject().lecture(soireeId, () -> ManchesDePassage
                        .palmaresAAfficher(depot.deLaSoiree(soireeId), ModeDeJeu.FREESTYLE)
                        .map(manche -> classer(soireeId, manche.getMancheId())).orElse(null));
                fait = true;
            }
            if (palmares == null) { return null; }
            return Destination.CONSOLE.equals(destination) ? palmares
                    : palmares.stream().filter(ligne -> ligne.rang() != null).limit(3).toList();
        }
    }

    private List<VuesFreestyle.LignePalmares> classer(UUID soireeId, UUID mancheId) {
        var classement = ClassementParVote.classer(passages.resultatsDeVote(List.of(mancheId)));
        var contenus = persistance.contenus(mancheId);
        var pseudos = participants.pseudos(soireeId);
        return classement.stream()
                .map(ligne -> VuesFreestyle.lignePalmares(ligne, contenus, pseudos)).toList();
    }
}
