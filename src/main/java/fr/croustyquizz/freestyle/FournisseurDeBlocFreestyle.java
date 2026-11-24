package fr.croustyquizz.freestyle;

import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.passage.ParticipantsDePassage;
import fr.croustyquizz.tempsreel.FournisseurDeBloc;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
final class FournisseurDeBlocFreestyle implements FournisseurDeBloc {
    private final DepotDeManches depot;
    private final ObjectProvider<ServiceFreestyle> services;
    private final ParticipantsDePassage participants;
    private final PersistanceFreestyleJpa persistance;

    FournisseurDeBlocFreestyle(DepotDeManches depot, ObjectProvider<ServiceFreestyle> services,
            ParticipantsDePassage participants, PersistanceFreestyleJpa persistance) {
        this.depot = depot;
        this.services = services;
        this.participants = participants;
        this.persistance = persistance;
    }

    @Override public ModeDeJeu mode() { return ModeDeJeu.FREESTYLE; }

    @Override public Object fournir(UUID soireeId, Destination destination) {
        return preparer(soireeId).apply(destination);
    }

    @Override public Function<Destination, Object> preparer(UUID soireeId) {
        var service = services.getObject();
        return service.lecture(soireeId, () -> {
            var active = depot.deLaSoiree(soireeId).stream().filter(m -> m.getMode() == mode()
                    && (m.getEtat() == EtatManche.EN_COURS || m.getEtat() == EtatManche.EN_PAUSE)).findFirst();
            if (active.isEmpty()) { return destination -> null; }
            var manche = active.get();
            var instantane = (InstantaneFreestyle) service.instantane(soireeId, manche.getMancheId());
            var pseudos = participants.pseudos(soireeId);
            var disponibles = persistance.instrumentales(soireeId);
            return destination -> VuesFreestyle.projeter(instantane, destination, pseudos, disponibles, manche.getModifieLe())
                    .orElse(null);
        });
    }
}
