package fr.croustyquizz.freestyle;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReglagesFreestyle(ThemeDeRimes theme, SchemaDeRimes schemaDeRimes, DifficulteFreestyle difficulte,
        int nombreMesures, List<UUID> instrumentaleIds) {

    public ReglagesFreestyle {
        Objects.requireNonNull(theme);
        Objects.requireNonNull(schemaDeRimes);
        Objects.requireNonNull(difficulte);
        if (nombreMesures != 8 && nombreMesures != 16 && nombreMesures != 32) {
            throw new IllegalArgumentException("Une Grille comporte 8, 16 ou 32 mesures.");
        }
        instrumentaleIds = List.copyOf(instrumentaleIds);
        if (instrumentaleIds.isEmpty() || new HashSet<>(instrumentaleIds).size() != instrumentaleIds.size()) {
            throw new IllegalArgumentException("Choisir au moins une Instrumentale, sans doublon.");
        }
    }

    List<Instrumentale> selectionDisponible(List<Instrumentale> disponibles) {
        return disponibles.stream().filter(fiche -> instrumentaleIds.contains(fiche.instrumentaleId())).toList();
    }
}
