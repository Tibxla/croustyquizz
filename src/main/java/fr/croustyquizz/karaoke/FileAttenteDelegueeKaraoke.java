package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.Pouvoir;
import fr.croustyquizz.tempsreel.DonneesDeDelegation;
import java.util.UUID;

/**
 * File d'attente du pouvoir délégué FILE_ATTENTE (#67), tirée de la projection de la Console sous le même
 * verrou ; null hors Manche de Karaoké en cours ou en pause.
 */
public final class FileAttenteDelegueeKaraoke implements DonneesDeDelegation {
    private final FournisseurDeBlocKaraoke bloc;

    public FileAttenteDelegueeKaraoke(FournisseurDeBlocKaraoke bloc) { this.bloc = bloc; }

    @Override public Pouvoir pouvoir() { return Pouvoir.FILE_ATTENTE; }

    @Override public String champ() { return "fileAttente"; }

    @Override public Object fournir(UUID soireeId) {
        Object console = bloc.preparer(soireeId).apply(Destination.CONSOLE);
        return console instanceof VuesKaraoke.BlocKaraokeConsole projection ? VuesKaraoke.FileAttenteDeleguee.de(projection) : null;
    }
}
