package fr.croustyquizz.freestyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DictionnaireDeRimesTest {
    @Test
    void rapprocheLesSonsPlutotQueLesLettresEtTrieParFrequenceDansChaqueNiveau() {
        DictionnaireDeRimes dictionnaire = new DictionnaireDeRimesPhonetique(List.of(
                entree("peau", "p o", 100),
                entree("drapeau", "a p o", 50),
                entree("chapeau", "a p o", 30),
                entree("gâteau", "a t o", 90),
                entree("chaud", "ʃ o", 80),
                entree("cadeau", "a d o", 80),
                entree("gauche", "g o ʃ", 110),
                entree("port", "p ɔ ʁ", 110)), List.of());

        assertThat(dictionnaire.rimesDe("drapeau"))
                .containsExactly("chapeau", "peau", "gâteau", "cadeau", "chaud");
    }

    @Test
    void ecarteLesMotsFiltresMemeSiLaSourceChangeLeurCasseOuLeursAccents() {
        DictionnaireDeRimes dictionnaire = new DictionnaireDeRimesPhonetique(List.of(
                entree("sacré", "a k ʁ e", 20),
                entree("sucré", "y k ʁ e", 10),
                entree("FOUTRÉ", "t ʁ e", 100)), List.of(" foutre "));

        assertThat(dictionnaire.rimesDe(" SACRE\u0301 ")).containsExactly("sucré");
        assertThat(dictionnaire.rimesDe("foutré")).isEmpty();
    }

    private static EntreePhonetique entree(String mot, String sons, double frequence) {
        return new EntreePhonetique(mot, List.of(sons.split(" ")), frequence, ThemeDeRimes.SOIREE);
    }
}
