package fr.croustyquizz.karaoke;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Lecture immuable, avec son calage et l’éventuelle demande adressée au lecteur. */
public record LecturePassage(UUID lectureId, UUID passageId, UUID lecteurId, EtatLecture etat,
        SourceLecture source, FichierAudioLocal fichier, MotifParolesSeules motifParolesSeules,
        OrigineParoles origineParoles, ParolesDuPassage paroles, long versionSourceId,
        long decalageMs, long numeroModificationCalage, UUID demandeId, Instant demandeeLe) {
    private static final long ENTIER_MAX = 9_007_199_254_740_991L;

    public LecturePassage {
        Objects.requireNonNull(lectureId);
        Objects.requireNonNull(passageId);
        Objects.requireNonNull(etat);
        Objects.requireNonNull(origineParoles);
        Objects.requireNonNull(paroles);
        LecturePreparee.verifierSource(source, fichier, motifParolesSeules);
        boolean departEnParolesSeules = source == SourceLecture.PAROLES_SEULES
                && motifParolesSeules != MotifParolesSeules.ERREUR_LECTURE;
        if ((demandeId == null) != (demandeeLe == null)
                || (source == SourceLecture.PAROLES_SEULES && (etat == EtatLecture.EN_ERREUR || demandeId != null))
                || departEnParolesSeules != (origineParoles == OrigineParoles.INSCRIPTION)
                || decalageMs < -ENTIER_MAX || decalageMs > ENTIER_MAX
                || numeroModificationCalage < 0 || numeroModificationCalage > ENTIER_MAX) {
            throw new IllegalArgumentException("L’état de la lecture est incompatible avec sa source ou son calage.");
        }
    }

    LecturePassage passerA(EtatLecture nouvelEtat) {
        return new LecturePassage(lectureId, passageId, lecteurId, nouvelEtat, source, fichier, motifParolesSeules,
                origineParoles, paroles, versionSourceId, decalageMs, numeroModificationCalage, null, null);
    }

    LecturePassage sansDemande() { return demandeId == null ? this : passerA(etat); }

    LecturePassage reprendreEnParolesSeules() {
        return new LecturePassage(lectureId, passageId, lecteurId, EtatLecture.DEMARREE,
                SourceLecture.PAROLES_SEULES, null, MotifParolesSeules.ERREUR_LECTURE, origineParoles,
                paroles, versionSourceId, decalageMs, numeroModificationCalage, null, null);
    }

    LecturePassage avecCalage(long decalage, UUID demande, Instant demandee) {
        long compteur = decalage == decalageMs ? numeroModificationCalage : numeroModificationCalage + 1;
        return new LecturePassage(lectureId, passageId, lecteurId, etat, source, fichier, motifParolesSeules,
                origineParoles, paroles, versionSourceId, decalage, compteur, demande, demandee);
    }

    long dureeReferenceMs() {
        Long dureeFichier = fichier == null ? null : fichier.dureeMs();
        return dureeFichier != null && dureeFichier > 0 ? dureeFichier : paroles.dureeMs();
    }
}
