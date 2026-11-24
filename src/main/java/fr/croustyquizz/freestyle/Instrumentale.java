package fr.croustyquizz.freestyle;

import java.util.Objects;
import java.util.UUID;

/** Fiche échangée avec l’Écran de salle. L’identifiant y désigne un fichier local. */
public record Instrumentale(UUID instrumentaleId, String titre, int tempoBpm, long departBeatMs) {
    static final long POSITION_MAX_MS = 9_007_199_254_740_991L;

    public Instrumentale {
        Objects.requireNonNull(instrumentaleId);
        if (titre == null || titre.isBlank() || titre.codePointCount(0, titre.length()) > 120) {
            throw new IllegalArgumentException("Le titre de l’Instrumentale doit contenir de 1 à 120 caractères.");
        }
        if (tempoBpm < 1 || tempoBpm > 1000 || departBeatMs < 0 || departBeatMs > POSITION_MAX_MS) {
            throw new IllegalArgumentException("Le tempo ou le départ du beat est invalide.");
        }
    }

    long positionSurLeFichier(int tempsEcoules) {
        return departBeatMs + Math.round(tempsEcoules * 60_000.0 / tempoBpm);
    }
}
