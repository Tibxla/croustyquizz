package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParolesLrcTest {
    @Test
    void laLectureSoupleIgnoreLeMalFormeEtCeQuiDepasseLaDuree() {
        assertThat(ParolesLrc.lire("[00:02.00]Deux\n[00:01.5]Un\nsans balise\n[00:09.00]Trop tard", 5000L))
                .containsExactly(new LigneParoles(2000, "Deux"));
        assertThat(ParolesLrc.lire(null, 5000L)).isEmpty();
    }

    @Test
    void laLectureStricteAccepteLesMetadonneesLesLignesVidesEtLesBalisesMultiples() {
        String lrc = "\uFEFF[ti:La lanterne]\n[ar:Les Lucioles]\n[length: 00:04]\n\n"
                + "[00:00.50]Une lanterne\r\n[00:02.50][00:01.50]Refrain\n[00:03.000]Fin\n";
        assertThat(ParolesLrc.lireStrictement(lrc, 4000)).containsExactly(new LigneParoles(500, "Une lanterne"),
                new LigneParoles(1500, "Refrain"), new LigneParoles(2500, "Refrain"), new LigneParoles(3000, "Fin"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[00:00.50]Une ligne\nsans balise",
        "[00:00.50]Une ligne\n[0:61.00]Secondes impossibles",
        "[00:00.50]Une ligne\n[00:04.00]Au-delà de la durée",
        "[00:00.50]Une ligne\n[offset:+200]",
        "[ti:Seulement des métadonnées]\n[00:01.00]",
        "",
    })
    void laLectureStricteRefuseCeQueLaSoupleIgnorerait(String lrc) {
        assertThatIllegalArgumentException().isThrownBy(() -> ParolesLrc.lireStrictement(lrc, 4000));
    }

    @Test
    void laLectureStricteSignaleLaLigneFautive() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ParolesLrc.lireStrictement("[00:00.50]Bon\n[00:09.00]Trop tard", 4000))
                .withMessageContaining("ligne 2");
        assertThat(ParolesLrc.lireStrictement("[00:00.50]Bon", 4000)).isEqualTo(List.of(new LigneParoles(500, "Bon")));
    }
}
