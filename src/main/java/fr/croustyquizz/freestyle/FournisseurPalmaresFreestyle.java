package fr.croustyquizz.freestyle;

import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.passage.ClassementParVote;
import fr.croustyquizz.passage.ParticipantsDePassage;
import fr.croustyquizz.passage.PersistancePassageJpa;
import fr.croustyquizz.tempsreel.FournisseurDeChampCommun;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
final class FournisseurPalmaresFreestyle implements FournisseurDeChampCommun {
    private static final Comparator<ManchePersistante> ORDRE_DES_FINS = Comparator
            .comparing(ManchePersistante::getModifieLe).thenComparing(ManchePersistante::getMancheId);
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
        if (!concerne(destination) || partieCommune.get("mancheActive") != null) { return null; }
        return services.getObject().lecture(soireeId, () -> {
            var derniere = depot.deLaSoiree(soireeId).stream().filter(manche -> manche.getEtat() == EtatManche.TERMINEE)
                    .max(ORDRE_DES_FINS).filter(manche -> manche.getMode() == ModeDeJeu.FREESTYLE);
            return derniere.map(manche -> classer(soireeId, manche.getMancheId(), destination)).orElse(null);
        });
    }

    private List<VuesFreestyle.LignePalmares> classer(UUID soireeId, UUID mancheId, Destination destination) {
        var classement = ClassementParVote.classer(passages.resultatsDeVote(List.of(mancheId))).stream();
        if (Destination.SALLE.equals(destination)) {
            classement = classement.filter(ligne -> ligne.rang() != null).limit(3);
        }
        var contenus = persistance.contenus(mancheId);
        var pseudos = participants.pseudos(soireeId);
        return classement.map(ligne -> VuesFreestyle.lignePalmares(ligne, contenus, pseudos)).toList();
    }
}
