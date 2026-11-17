package fr.croustyquizz.karaoke;

import fr.croustyquizz.passage.EtatFileAttente;
import fr.croustyquizz.manche.EtatManche;
import java.util.Map;
import java.util.UUID;

/** Capture immuable, contenus de tous les Passages compris. */
public record InstantaneKaraoke(EtatManche etatManche, EtatFileAttente file, Map<UUID, ContenuKaraoke> contenus) {
    public InstantaneKaraoke { contenus = Map.copyOf(contenus); }
}
