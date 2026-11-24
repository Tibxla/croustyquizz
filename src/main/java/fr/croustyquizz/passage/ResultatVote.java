package fr.croustyquizz.passage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Résultat d'un Vote du public clos. {@code nombreVotes} ne compte que les notes ; {@code voix} compte les
 * désignations de chaque Interprète d'un Passage de Freestyle à plusieurs (#74), vide sinon.
 */
public record ResultatVote(UUID passageId, UUID mancheId, long numero, List<UUID> interpretes,
        long nombreVotes, long sommeNotes, Integer moyenneDixiemes, Map<UUID, Long> voix) {
    public ResultatVote {
        interpretes = List.copyOf(interpretes);
        voix = Map.copyOf(voix);
    }

    public ResultatVote(UUID passageId, UUID mancheId, long numero, List<UUID> interpretes,
            long nombreVotes, long sommeNotes, Integer moyenneDixiemes) {
        this(passageId, mancheId, numero, interpretes, nombreVotes, sommeNotes, moyenneDixiemes, Map.of());
    }
}
