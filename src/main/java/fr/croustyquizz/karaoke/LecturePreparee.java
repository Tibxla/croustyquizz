package fr.croustyquizz.karaoke;

import java.util.List;

/** Résultat de la préparation, à valider contre le courant et le lecteur désigné. */
public record LecturePreparee(SourceLecture source, FichierAudioLocal fichier, MotifParolesSeules motifParolesSeules,
        OrigineParoles origineParoles, long dureeParolesMs, List<LigneParoles> lignes,
        long versionSourceId, long decalageInitialMs) {
    public LecturePreparee {
        LecturePassage.verifierSourceEtOrigine(source, fichier, motifParolesSeules, origineParoles);
        if (motifParolesSeules == MotifParolesSeules.ECRAN_NON_PRET || motifParolesSeules == MotifParolesSeules.ERREUR_LECTURE) {
            throw new IllegalArgumentException("Ce motif de paroles seules est réservé au déroulement.");
        }
        LecturePassage.verifierDecalage(decalageInitialMs);
        lignes = List.copyOf(lignes);
        ParolesDuPassage.verifierContenu(dureeParolesMs, lignes);
    }
}
