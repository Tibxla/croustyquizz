package fr.croustyquizz.karaoke;

import fr.croustyquizz.passage.EtatCyclePassage;
import fr.croustyquizz.passage.EtatFileAttente;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Capture immuable, contenus de tous les Passages compris. */
public record EtatKaraokePersiste(EtatFileAttente file, Map<UUID, ContenuKaraoke> contenus,
        EtatCyclePassage cycle, Map<UUID, LecturePassage> lectures, UUID lecteurDesigneId) {
    public EtatKaraokePersiste {
        cycle = Objects.requireNonNullElseGet(cycle, EtatCyclePassage::vide);
        contenus = Map.copyOf(contenus);
        lectures = Map.copyOf(lectures);
    }

    public EtatKaraokePersiste(EtatFileAttente file, Map<UUID, ContenuKaraoke> contenus) {
        this(file, contenus, EtatCyclePassage.vide(), Map.of(), null);
    }
}
