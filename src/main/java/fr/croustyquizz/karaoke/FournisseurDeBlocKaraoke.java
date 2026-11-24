package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.ParticipantsDePassage;
import fr.croustyquizz.tempsreel.FournisseurDeBloc;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;

/** Projection de la seule Manche Karaoké active, sous le verrou de l'assembleur. */
public final class FournisseurDeBlocKaraoke implements FournisseurDeBloc {
    private final DepotDeManches depot;
    private final ObjectProvider<ServiceKaraoke> service;
    private final ParticipantsDePassage participants;
    private final ProprietesKaraoke proprietes;
    private final ProprietesSourceKaraoke source;
    public FournisseurDeBlocKaraoke(DepotDeManches depot, ObjectProvider<ServiceKaraoke> service,
            ParticipantsDePassage participants, ProprietesKaraoke proprietes, ProprietesSourceKaraoke source) {
        this.depot = depot; this.service = service; this.participants = participants;
        this.proprietes = proprietes; this.source = source;
    }
    @Override public ModeDeJeu mode() { return ModeDeJeu.KARAOKE; }
    @Override public Object fournir(UUID soireeId, Destination destination) {
        var active = depot.deLaSoiree(soireeId).stream().filter(m -> m.getMode() == mode()
                && (m.getEtat() == EtatManche.EN_COURS || m.getEtat() == EtatManche.EN_PAUSE)).findFirst();
        if (active.isEmpty()) { return null; }
        var instantane = (InstantaneKaraoke) service.getObject().instantane(soireeId, active.get().getMancheId());
        return VuesKaraoke.projeter(instantane, destination, participants, active.get().getModifieLe(), proprietes, source);
    }
}
