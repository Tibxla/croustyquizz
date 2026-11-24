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
        Objects.requireNonNull(paroles);
        verifierSourceEtOrigine(source, fichier, motifParolesSeules, origineParoles);
        verifierDecalage(decalageMs);
        if ((demandeId == null) != (demandeeLe == null)
                || (source == SourceLecture.PAROLES_SEULES && (etat == EtatLecture.EN_ERREUR || demandeId != null))
                || numeroModificationCalage < 0 || numeroModificationCalage > ENTIER_MAX) {
            throw new IllegalArgumentException("L’état de la lecture est incompatible avec sa source ou son calage.");
        }
    }

    static void verifierSourceEtOrigine(SourceLecture source, FichierAudioLocal fichier,
            MotifParolesSeules motif, OrigineParoles origine) {
        boolean local = source == SourceLecture.FICHIER_LOCAL;
        if (source == null || local != (fichier != null) || local == (motif != null)) {
            throw new IllegalArgumentException("La source de lecture exige soit un fichier local, soit un motif de paroles seules.");
        }
        boolean departEnParolesSeules = source == SourceLecture.PAROLES_SEULES && motif != MotifParolesSeules.ERREUR_LECTURE;
        if (origine == null || departEnParolesSeules != (origine == OrigineParoles.INSCRIPTION)) {
            throw new IllegalArgumentException("L’origine des paroles est incompatible avec la source de lecture.");
        }
    }

    static void verifierDecalage(long decalage) {
        if (decalage < -ENTIER_MAX || decalage > ENTIER_MAX) {
            throw new IllegalArgumentException("Le décalage dépasse les bornes du contrat.");
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
