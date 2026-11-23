package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.passage.EtatCyclePassage;
import fr.croustyquizz.passage.EtatFileAttente;
import java.util.Map;
import java.util.UUID;

/** Capture immuable, contenus de tous les Passages compris. */
public record InstantaneKaraoke(EtatManche etatManche, EtatFileAttente file, Map<UUID, ContenuKaraoke> contenus,
        EtatCyclePassage cycle, Map<UUID, LecturePassage> lectures, UUID lecteurDesigneId) {
    public InstantaneKaraoke {
        contenus = Map.copyOf(contenus);
        lectures = Map.copyOf(lectures);
    }

    public InstantaneKaraoke(EtatManche etatManche, EtatFileAttente file, Map<UUID, ContenuKaraoke> contenus) {
        this(etatManche, file, contenus, EtatCyclePassage.vide(), Map.of(), null);
    }
}
