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
        DictionnaireDeRimes dictionnaire = ListesThematiquesDeRimes.charger();

        assertThat(dictionnaire.rimesDe(depart, theme))
                .hasSizeGreaterThanOrEqualTo(32)
                .doesNotHaveDuplicates()
                .doesNotContain(depart)
                .contains(exemple);
    }

    @Test
    void limiteLesSuggestionsAuThemeDemande() {
        DictionnaireDeRimes dictionnaire = ListesThematiquesDeRimes.charger();

        assertThat(dictionnaire.rimesDe("ballon")).contains("bouchon", "peloton").doesNotHaveDuplicates();
        assertThat(dictionnaire.rimesDe("ballon", ThemeDeRimes.SPORT))
                .contains("peloton").doesNotContain("bouchon");
        assertThat(dictionnaire.rimesDe("ballon", ThemeDeRimes.SOIREE))
                .contains("bouchon").doesNotContain("peloton");
        assertThat(dictionnaire.rimesDe("ballon", ThemeDeRimes.VILLE)).isEmpty();
    }

    @Test
    void neDevinePasLaPrononciationDUnMotAbsent() {
        DictionnaireDeRimes dictionnaire = ListesThematiquesDeRimes.charger();

        assertThat(dictionnaire.rimesDe("abricot")).isEmpty();
        assertThat(dictionnaire.rimesDe(" ")).isEmpty();
    }
}
