package fr.croustyquizz.freestyle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ListesThematiquesDeRimesTest {
    @ParameterizedTest
    @CsvSource({
        "SOIREE, soirée, préparer",
        "SOIREE, chanson, boisson",
        "VILLE, village, passage",
        "VILLE, lumière, rivière",
        "SPORT, jouer, marquer",
        "SPORT, ballon, peloton"
    })
    void chaqueFamilleRemplitTrenteDeuxMesuresDistinctes(ThemeDeRimes theme, String depart, String exemple) {
        DictionnaireDeRimes dictionnaire = DictionnaireDeRimesFourni.partage();

        assertThat(dictionnaire.rimesDe(depart, theme))
                .hasSizeGreaterThanOrEqualTo(32)
                .doesNotHaveDuplicates()
                .doesNotContain(depart)
                .contains(exemple);
    }

    @Test
    void prefereLeThemeDemandeQuandSaFamilleRemplitUneGrille() {
        DictionnaireDeRimes dictionnaire = DictionnaireDeRimesFourni.partage();

        assertThat(dictionnaire.rimesDe("ballon")).contains("bouchon", "peloton").doesNotHaveDuplicates();
        assertThat(dictionnaire.rimesDe("ballon", ThemeDeRimes.SPORT))
                .contains("peloton").doesNotContain("bouchon");
        assertThat(dictionnaire.rimesDe("ballon", ThemeDeRimes.SOIREE))
                .contains("bouchon").doesNotContain("peloton");
        assertThat(dictionnaire.rimesDe("ballon", ThemeDeRimes.VILLE))
                .as("le thème Ville n'a aucun mot en « on » : le lexique général le complète")
                .contains("bouchon", "peloton", "maison").doesNotHaveDuplicates();
    }

    @Test
    void neDevinePasLaPrononciationDUnMotAbsent() {
        DictionnaireDeRimes dictionnaire = DictionnaireDeRimesFourni.partage();

        assertThat(dictionnaire.rimesDe("croustimolle")).isEmpty();
        assertThat(dictionnaire.rimesDe(" ")).isEmpty();
    }
}
