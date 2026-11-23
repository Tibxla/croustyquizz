package fr.croustyquizz.karaoke;

import java.util.List;
import java.util.Objects;

/** Résultat de la préparation, à valider contre le courant et le lecteur désigné. */
public record LecturePreparee(SourceLecture source, FichierAudioLocal fichier, MotifParolesSeules motifParolesSeules,
        OrigineParoles origineParoles, long dureeParolesMs, List<LigneParoles> lignes,
        long versionSourceId, long decalageInitialMs) {
    public LecturePreparee {
        verifierSource(source, fichier, motifParolesSeules);
        Objects.requireNonNull(origineParoles);
        lignes = List.copyOf(lignes);
    }

    static void verifierSource(SourceLecture source, FichierAudioLocal fichier, MotifParolesSeules motif) {
        boolean local = Objects.requireNonNull(source) == SourceLecture.FICHIER_LOCAL;
        if (local != (fichier != null) || local == (motif != null)) {
            throw new IllegalArgumentException("La source de lecture exige soit un fichier local, soit un motif de paroles seules.");
        }
    }
}
