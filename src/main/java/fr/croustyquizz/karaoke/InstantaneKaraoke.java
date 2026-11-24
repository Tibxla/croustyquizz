package fr.croustyquizz.karaoke;

import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.passage.EtatCyclePassage;
import fr.croustyquizz.passage.EtatFileAttente;
import fr.croustyquizz.passage.EtatVoteDuPublic;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Capture immuable, contenus de tous les Passages compris. */
public record InstantaneKaraoke(EtatManche etatManche, EtatFileAttente file, Map<UUID, ContenuKaraoke> contenus,
        EtatCyclePassage cycle, Map<UUID, LecturePassage> lectures, UUID lecteurDesigneId,
        EtatVoteDuPublic voteDuPublic, boolean pilotageAutomatique) {
    public InstantaneKaraoke {
        cycle = Objects.requireNonNullElseGet(cycle, EtatCyclePassage::vide);
        contenus = Map.copyOf(contenus);
        lectures = Map.copyOf(lectures);
        voteDuPublic = Objects.requireNonNullElseGet(voteDuPublic, EtatVoteDuPublic::vide);
    }

    public InstantaneKaraoke(EtatManche etatManche, EtatFileAttente file, Map<UUID, ContenuKaraoke> contenus,
            EtatCyclePassage cycle, Map<UUID, LecturePassage> lectures, UUID lecteurDesigneId, EtatVoteDuPublic voteDuPublic) {
        this(etatManche, file, contenus, cycle, lectures, lecteurDesigneId, voteDuPublic, false);
    }

    public InstantaneKaraoke(EtatManche etatManche, EtatFileAttente file, Map<UUID, ContenuKaraoke> contenus,
            EtatCyclePassage cycle, Map<UUID, LecturePassage> lectures, UUID lecteurDesigneId) {
        this(etatManche, file, contenus, cycle, lectures, lecteurDesigneId, EtatVoteDuPublic.vide());
    }

    public InstantaneKaraoke(EtatManche etatManche, EtatFileAttente file, Map<UUID, ContenuKaraoke> contenus) {
        this(etatManche, file, contenus, EtatCyclePassage.vide(), Map.of(), null);
    }
}
