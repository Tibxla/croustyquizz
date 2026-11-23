package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class LecturePassageTest {
    private static final UUID ID = new UUID(0, 1);
    private static final Instant RECU = Instant.parse("2026-11-17T12:00:00Z");
    private static final FichierAudioLocal FICHIER = new FichierAudioLocal("Artiste - Titre.mp3", 100, 300_000L);
    private static final List<LigneParoles> LIGNES = List.of(new LigneParoles(12_000, "Paroles"));
    private static final ParolesDuPassage PAROLES = new ParolesDuPassage(ID, 290_000, LIGNES);

    @Test
    void fichierLocalExigeUnNomSansCheminEtUneTaillePositive() {
        for (String nom : List.of("", "dossier/titre.mp3", "dossier\\titre.mp3")) {
            assertThatIllegalArgumentException().isThrownBy(() -> new FichierAudioLocal(nom, 100, 300L));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new FichierAudioLocal("titre.mp3", 0, 300L));
        assertThatIllegalArgumentException().isThrownBy(() -> new FichierAudioLocal("titre.mp3", 100, -1L));
        assertThat(new FichierAudioLocal("titre.mp3", 100, null).dureeMs()).isNull();
        assertThat(new FichierAudioLocal("titre.mp3", 100, 0L).dureeMs()).isZero();
    }

    @Test
    void retientLesLignesExploitablesDansLOrdreSourceEtLesFige() {
        var lignes = new ArrayList<>(List.of(new LigneParoles(-1, "Filtrée"),
                new LigneParoles(0, " "), new LigneParoles(12_000, "Paroles"), new LigneParoles(2_000, "Suite")));
        var paroles = new ParolesDuPassage(ID, 290_000, lignes);
        var preparation = new LecturePreparee(SourceLecture.FICHIER_LOCAL, FICHIER, null,
                OrigineParoles.VERSION_ADAPTEE, 290_000, lignes, 42, 0);
        lignes.clear();
        assertThat(paroles.lignes()).containsExactly(new LigneParoles(0, " "), new LigneParoles(12_000, "Paroles"),
                new LigneParoles(2_000, "Suite"));
        assertThat(paroles.premierePosition()).isEqualTo(12_000L);
        assertThat(preparation.lignes()).hasSize(4);
        assertThatThrownBy(() -> paroles.lignes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> new ParolesDuPassage(ID, 1,
                List.of(new LigneParoles(1, "\u2003"), new LigneParoles(-1, "Filtrée"))));
        assertThatIllegalArgumentException().isThrownBy(() -> new ParolesDuPassage(ID, 0, LIGNES));
    }

    @Test
    void preparationAssocieExactementFichierOuMotifALaSource() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LecturePreparee(SourceLecture.FICHIER_LOCAL, null, null,
                OrigineParoles.VERSION_ADAPTEE, 290_000, LIGNES, 42, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new LecturePreparee(SourceLecture.FICHIER_LOCAL, FICHIER,
                MotifParolesSeules.FICHIER_ABSENT, OrigineParoles.VERSION_ADAPTEE, 290_000, LIGNES, 42, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new LecturePreparee(SourceLecture.PAROLES_SEULES, FICHIER,
                MotifParolesSeules.FICHIER_AMBIGU, OrigineParoles.INSCRIPTION, 290_000, LIGNES, 42, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new LecturePreparee(SourceLecture.PAROLES_SEULES, null,
                null, OrigineParoles.INSCRIPTION, 290_000, LIGNES, 42, 0));
        assertThat(new LecturePreparee(SourceLecture.PAROLES_SEULES, null, MotifParolesSeules.FICHIER_ILLISIBLE,
                OrigineParoles.INSCRIPTION, 290_000, LIGNES, 42, 0).fichier()).isNull();
    }

    @Test
    void lectureAppliqueLesInvariantsDuContrat() {
        assertThatIllegalArgumentException().isThrownBy(() -> lecture(EtatLecture.EN_ERREUR,
                OrigineParoles.INSCRIPTION, MotifParolesSeules.FICHIER_ABSENT, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> lecture(EtatLecture.DEMARREE,
                OrigineParoles.INSCRIPTION, MotifParolesSeules.FICHIER_ABSENT, ID, RECU));
        assertThatIllegalArgumentException().isThrownBy(() -> lecture(EtatLecture.DEMARREE,
                OrigineParoles.VERSION_ADAPTEE, null, ID, null));
        assertThatIllegalArgumentException().isThrownBy(() -> lecture(EtatLecture.DEMARREE,
                OrigineParoles.REPLI_INSCRIPTION, MotifParolesSeules.FICHIER_AMBIGU, null, null));
        assertThat(lecture(EtatLecture.DEMARREE, OrigineParoles.REPLI_INSCRIPTION, MotifParolesSeules.ERREUR_LECTURE,
                null, null).source()).isEqualTo(SourceLecture.PAROLES_SEULES);
    }

    private static LecturePassage lecture(EtatLecture etat, OrigineParoles origine, MotifParolesSeules motif,
            UUID demande, Instant demandeeLe) {
        SourceLecture source = motif == null ? SourceLecture.FICHIER_LOCAL : SourceLecture.PAROLES_SEULES;
        return new LecturePassage(ID, ID, ID, etat, source, motif == null ? FICHIER : null, motif,
                origine, PAROLES, 42, 0, 0, demande, demandeeLe);
    }
}
