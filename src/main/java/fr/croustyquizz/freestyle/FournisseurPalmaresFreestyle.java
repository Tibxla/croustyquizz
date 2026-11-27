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
        if (!concerne(destination) || partieCommune.get("mancheActive") != null) { return null; }
        return services.getObject().lecture(soireeId, () -> {
            var derniere = ManchesDePassage.palmaresAAfficher(depot.deLaSoiree(soireeId), ModeDeJeu.FREESTYLE);
            return derniere.map(manche -> classer(soireeId, manche.getMancheId(), destination)).orElse(null);
        });
    }

    /** Interprètes des Passages à plusieurs par victoires, puis Passages à un seul Interprète par note (#74). */
    private List<Object> classer(UUID soireeId, UUID mancheId, Destination destination) {
        var resultats = passages.resultatsDeVote(List.of(mancheId));
        var pseudos = participants.pseudos(soireeId);
        boolean salle = Destination.SALLE.equals(destination);
        var victoires = ClassementParVictoires.classer(resultats, pseudos).stream()
                .filter(ligne -> !salle || ligne.rang() != null).limit(salle ? 3 : Long.MAX_VALUE);
        var notes = ClassementParVote.classer(resultats.stream().filter(r -> r.interpretes().size() == 1).toList()).stream()
                .filter(ligne -> !salle || ligne.rang() != null).limit(salle ? 3 : Long.MAX_VALUE);
        var contenus = persistance.contenus(mancheId);
        return java.util.stream.Stream.concat(victoires.map(Object.class::cast),
                notes.map(ligne -> VuesFreestyle.lignePalmares(ligne, contenus, pseudos))).toList();
    }
}
