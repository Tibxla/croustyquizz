package fr.croustyquizz.freestyle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LexiqueDeRimesTest {
    private static final String EN_TETE = "ortho\tphon\tlemme\tcgram\tfreqfilms2\n";

    @Test
    void rimeParLaPrononciationDeLexiqueAvecLesRimesRichesEnTete() throws IOException {
        DictionnaireDeRimes dictionnaire = dictionnaireDEssai(List.of());

        assertThat(dictionnaire.rimesDe("soirée")).containsExactly("marée", "café", "entrée");
    }

    @Test
    void ometLeEMuetFinalSaufSIlEstLaSeuleVoyelle() throws IOException {
        DictionnaireDeRimes dictionnaire = dictionnaireDEssai(List.of());

        assertThat(dictionnaire.rimesDe("blanche")).containsExactly("manche");
        assertThat(dictionnaire.rimesDe("de")).containsExactly("le");
    }

    @Test
    void gardeChaquePrononciationDUnMemeMot() throws IOException {
        DictionnaireDeRimes dictionnaire = dictionnaireDEssai(List.of());

        assertThat(dictionnaire.rimesDe("fils")).containsExactly("ville", "vis");
    }

    @Test
    void lesFormesFlechiesDUnMotFiltreDisparaissentParLeurLemme() throws IOException {
        DictionnaireDeRimes dictionnaire = dictionnaireDEssai(List.of("con"));

        assertThat(dictionnaire.rimesDe("bonne")).containsExactly("sonne");
        assertThat(dictionnaire.rimesDe("connes")).isEmpty();
    }

    @Test
    void convertitChaqueSymboleDeLexiqueEnSonUnique() {
        assertThat(LexiqueDeRimes.sons("S@s§")).containsExactly("ʃ", "ɑ̃", "s", "ɔ̃");
        assertThat(LexiqueDeRimes.sons("p2tEtR")).containsExactly("p", "ø", "t", "ɛ", "t", "ʁ");
        assertThat(LexiqueDeRimes.sons("l°5NOk8ZG19x")).containsExactly(
                "l", "ə", "ɛ̃", "ɲ", "ɔ", "k", "ɥ", "ʒ", "ŋ", "œ̃", "œ", "x");
    }

    @Test
    void refuseUnEnTeteInattendu() {
        assertThatThrownBy(() -> lire("ortho\tphon\tfreqfilms2\n"))
                .isInstanceOf(IOException.class).hasMessageContaining("En-tête");
    }

    @Test
    void situeUneLigneInvalide() {
        assertThatThrownBy(() -> lire(EN_TETE + "soirée\tswaRe\tsoirée\tNOM\t94.36\nmarée\tmaRe\tNOM\t5\n"))
                .isInstanceOf(IOException.class).hasMessageContaining("essai:3");
        assertThatThrownBy(() -> lire(EN_TETE + "soirée\tswaQe\tsoirée\tNOM\t94.36\n"))
                .isInstanceOf(IOException.class).hasMessageContaining("essai:2")
                .rootCause().hasMessageContaining("Q");
        assertThatThrownBy(() -> lire(EN_TETE + "pff\tpf\tpff\tONO\t1.59\n"))
                .isInstanceOf(IOException.class).hasMessageContaining("essai:2");
    }

    @Test
    void lesEntreesDuLexiqueNOntPasDeThemeEtGardentLeursLemmes() throws IOException {
        List<EntreePhonetique> entrees = lire(EN_TETE + "entrée\t@tRe\tentrer,entrée\tNOM,VER\t56.91\n");

        assertThat(entrees).singleElement().satisfies(entree -> {
            assertThat(entree.theme()).isNull();
            assertThat(entree.lemmes()).containsExactly("entrer", "entrée");
            assertThat(entree.frequence()).isEqualTo(56.91);
        });
    }

    private static List<EntreePhonetique> lire(String contenu) throws IOException {
        return LexiqueDeRimes.lire(new BufferedReader(new StringReader(contenu)), "essai");
    }

    private static DictionnaireDeRimes dictionnaireDEssai(List<String> filtre) throws IOException {
        InputStream flux = LexiqueDeRimesTest.class.getResourceAsStream("/freestyle/lexique-essai.tsv");
        try (BufferedReader lecteur = new BufferedReader(new InputStreamReader(flux, StandardCharsets.UTF_8))) {
            return new DictionnaireDeRimesPhonetique(LexiqueDeRimes.lire(lecteur, "lexique-essai.tsv"), filtre);
        }
    }
}
