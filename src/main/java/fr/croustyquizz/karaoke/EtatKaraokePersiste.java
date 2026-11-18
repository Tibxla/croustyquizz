package fr.croustyquizz.karaoke;

import fr.croustyquizz.passage.EtatFileAttente;
import java.util.Map;
import java.util.UUID;

/** Capture immuable, contenus de tous les Passages compris. */
public record EtatKaraokePersiste(EtatFileAttente file, Map<UUID, ContenuKaraoke> contenus) {
    public EtatKaraokePersiste { contenus = Map.copyOf(contenus); }
}
