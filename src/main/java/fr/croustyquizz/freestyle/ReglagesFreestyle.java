package fr.croustyquizz.freestyle;

import fr.croustyquizz.manche.Refuse;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** {@code mesuresParCouplet} (#74) vaut 4 s’il est absent, notamment dans les réglages enregistrés avant lui. */
public record ReglagesFreestyle(ThemeDeRimes theme, SchemaDeRimes schemaDeRimes, DifficulteFreestyle difficulte,
        int nombreMesures, Integer mesuresParCouplet, List<UUID> instrumentaleIds) {
    static final int MESURES_PAR_COUPLET_PAR_DEFAUT = 4;
    private static final Set<Integer> LONGUEURS_DE_COUPLET = Set.of(2, 4, 8);

    public ReglagesFreestyle {
        Objects.requireNonNull(theme);
        Objects.requireNonNull(schemaDeRimes);
        Objects.requireNonNull(difficulte);
        if (nombreMesures != 8 && nombreMesures != 16 && nombreMesures != 32) {
            throw new IllegalArgumentException("Une Grille comporte 8, 16 ou 32 mesures.");
        }
        if (mesuresParCouplet == null) { mesuresParCouplet = MESURES_PAR_COUPLET_PAR_DEFAUT; }
        if (!LONGUEURS_DE_COUPLET.contains(mesuresParCouplet)) {
            throw new IllegalArgumentException("Un Couplet dure 2, 4 ou 8 mesures.");
        }
        instrumentaleIds = List.copyOf(instrumentaleIds);
        if (instrumentaleIds.isEmpty() || new HashSet<>(instrumentaleIds).size() != instrumentaleIds.size()) {
            throw new IllegalArgumentException("Choisir au moins une Instrumentale, sans doublon.");
        }
    }

    public ReglagesFreestyle(ThemeDeRimes theme, SchemaDeRimes schemaDeRimes, DifficulteFreestyle difficulte,
            int nombreMesures, List<UUID> instrumentaleIds) {
        this(theme, schemaDeRimes, difficulte, nombreMesures, null, instrumentaleIds);
    }

    /**
     * La Grille se découpe en Couplets entiers. Avec les valeurs permises (8, 16 ou 32 mesures, Couplets de 2, 4
     * ou 8), toutes les combinaisons divisent : la règle reste vérifiée à la création de la Manche.
     */
    static Optional<Refuse> refusDesCouplets(int nombreMesures, int mesuresParCouplet) {
        return nombreMesures % mesuresParCouplet == 0 ? Optional.empty() : Optional.of(new Refuse(422, "DONNEES_INVALIDES",
                "Le nombre de mesures de la Grille doit être un multiple de la longueur des Couplets."));
    }

    int nombreCouplets() { return nombreMesures / mesuresParCouplet; }

    List<Instrumentale> selectionDisponible(List<Instrumentale> disponibles) {
        return disponibles.stream().filter(fiche -> instrumentaleIds.contains(fiche.instrumentaleId())).toList();
    }
}
