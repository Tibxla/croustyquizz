package fr.croustyquizz.passage;

import java.util.List;
import java.util.UUID;

/** Résultat d'un Vote du public clos. */
public record ResultatVote(UUID passageId, UUID mancheId, long numero, List<UUID> interpretes,
        long nombreVotes, long sommeNotes, Integer moyenneDixiemes) {
    public ResultatVote {
        interpretes = List.copyOf(interpretes);
    }
}
