package fr.croustyquizz.karaoke;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Lecture immuable, avec son calage et l’éventuelle demande adressée au lecteur. */
public record LecturePassage(UUID lectureId, UUID passageId, UUID lecteurId, EtatLecture etat,
        SourceLecture source, FichierAudioLocal fichier, MotifParolesSeules motifParolesSeules,
        OrigineParoles origineParoles, ParolesDuPassage paroles, long versionSourceId,
        long decalageMs, long numeroModificationCalage, UUID demandeId, Instant demandeeLe) {
    public LecturePassage {
        Objects.requireNonNull(lectureId);
        Objects.requireNonNull(passageId);
        Objects.requireNonNull(etat);
        Objects.requireNonNull(origineParoles);
        Objects.requireNonNull(paroles);
        LecturePreparee.verifierSource(source, fichier, motifParolesSeules);
        if ((demandeId == null) != (demandeeLe == null)
                || (source == SourceLecture.PAROLES_SEULES && (etat == EtatLecture.EN_ERREUR || demandeId != null))
                || (source == SourceLecture.PAROLES_SEULES && origineParoles == OrigineParoles.REPLI_INSCRIPTION
                        && motifParolesSeules != MotifParolesSeules.ERREUR_LECTURE)
                || numeroModificationCalage < 0) {
            throw new IllegalArgumentException("L’état de la lecture est incompatible avec sa source ou son calage.");
        }
    }

    LecturePassage passerA(EtatLecture nouvelEtat) {
        return new LecturePassage(lectureId, passageId, lecteurId, nouvelEtat, source, fichier, motifParolesSeules,
                origineParoles, paroles, versionSourceId, decalageMs, numeroModificationCalage, null, null);
    }

    LecturePassage sansDemande() { return demandeId == null ? this : passerA(etat); }

    long dureeReferenceMs() {
        Long dureeFichier = fichier == null ? null : fichier.dureeMs();
        return dureeFichier != null && dureeFichier > 0 ? dureeFichier : paroles.dureeMs();
    }
}
