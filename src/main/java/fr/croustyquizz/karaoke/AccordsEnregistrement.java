package fr.croustyquizz.karaoke;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Accords des Passages en attente ou actifs, indexés par Interprète pour les projections privées ; suspendus compris si autorise est faux. */
record AccordsEnregistrement(boolean autorise, Map<UUID, List<UUID>> parInterprete) {
    List<UUID> de(UUID joueur) { return parInterprete.getOrDefault(joueur, List.of()); }
}
