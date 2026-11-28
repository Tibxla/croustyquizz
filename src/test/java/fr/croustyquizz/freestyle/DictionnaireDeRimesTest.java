package fr.croustyquizz.freestyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
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

        assertThat(dictionnaire.rimesDe(" SACRÉ ")).containsExactly("sucré");
        assertThat(dictionnaire.rimesDe("foutré")).isEmpty();
    }

    @Test
    void sansThemeLeLexiqueGeneralPasseAvantLesMotsQuIlNeContientPas() {
        DictionnaireDeRimes dictionnaire = new DictionnaireDeRimesPhonetique(List.of(
                entree("drapeau", "a p o", 90),
                entree("tempo", "ɑ̃ p o", 80),
                entree("studio", "d j o", 99),
                general("drapeau", "d ʁ a p o", 10),
                general("chapeau", "ʃ a p o", 5),
                general("peau", "p o", 50),
                general("tempo", "t ɑ̃ p o", 3)), List.of());

        assertThat(dictionnaire.rimesDe("drapeau")).containsExactly("chapeau", "peau", "tempo", "studio");
        assertThat(dictionnaire.rimesDe("peau")).containsExactly("drapeau", "chapeau", "tempo", "studio");
    }

    @Test
    void leLexiqueGeneralCompleteUneFamilleDuThemeTropPauvrePourUneGrille() {
        List<EntreePhonetique> entrees = new ArrayList<>(List.of(
                entree("drapeau", "a p o", 90),
                entree("cadeau", "a d o", 20),
                general("drapeau", "d ʁ a p o", 10),
                general("chapeau", "ʃ a p o", 5),
                general("cadeau", "k a d o", 40),
                general("peau", "p o", 50)));

        DictionnaireDeRimes pauvre = new DictionnaireDeRimesPhonetique(entrees, List.of());
        assertThat(pauvre.rimesDe("drapeau", ThemeDeRimes.SOIREE)).containsExactly("cadeau", "chapeau", "peau");
        assertThat(pauvre.rimesDe("drapeau", ThemeDeRimes.SPORT)).containsExactly("chapeau", "peau", "cadeau");

        for (int i = 0; i < DictionnaireDeRimesPhonetique.SUGGESTIONS_D_UNE_GRILLE; i++) {
            entrees.add(entree("mot" + i, "o", 1));
        }
        DictionnaireDeRimes riche = new DictionnaireDeRimesPhonetique(entrees, List.of());
        assertThat(riche.rimesDe("drapeau", ThemeDeRimes.SOIREE))
                .hasSize(DictionnaireDeRimesPhonetique.SUGGESTIONS_D_UNE_GRILLE + 1)
                .startsWith("cadeau").doesNotContain("chapeau", "peau");
    }

    @Test
    void unLemmeFiltreDuLexiqueEcarteSaFormeDeToutesLesSources() {
        DictionnaireDeRimes dictionnaire = new DictionnaireDeRimesPhonetique(List.of(
                entree("fous", "f u", 40),
                entree("roux", "ʁ u", 30),
                new EntreePhonetique("fous", List.of("f", "u"), 200, null, List.of("fou", "foutre")),
                new EntreePhonetique("foutue", List.of("f", "u", "t", "y"), 10, null, List.of("foutre", "foutu")),
                new EntreePhonetique("tortue", List.of("t", "ɔ", "ʁ", "t", "y"), 5, null, List.of("tortue")),
                general("doux", "d u", 60)), List.of("foutre"));

        assertThat(dictionnaire.rimesDe("roux")).containsExactly("doux");
        assertThat(dictionnaire.rimesDe("roux", ThemeDeRimes.SOIREE)).containsExactly("doux");
        assertThat(dictionnaire.rimesDe("tortue")).isEmpty();
        assertThat(dictionnaire.rimesDe("fous")).isEmpty();
    }

    @Test
    void neProposePasLesFlexionsHomophonesDuMotDeDepartMaisGardeSesHomophonesDistincts() {
        DictionnaireDeRimes dictionnaire = new DictionnaireDeRimesPhonetique(List.of(
                general("maison", "m ɛ z ɔ̃", 500),
                new EntreePhonetique("maisons", List.of("m", "ɛ", "z", "ɔ̃"), 90, null, List.of("maison")),
                general("raison", "ʁ ɛ z ɔ̃", 400),
                general("vert", "v ɛ ʁ", 80),
                new EntreePhonetique("verts", List.of("v", "ɛ", "ʁ"), 20, null, List.of("vert")),
                general("verre", "v ɛ ʁ", 60),
                general("hiver", "i v ɛ ʁ", 40)), List.of());

        assertThat(dictionnaire.rimesDe("maison")).containsExactly("raison");
        assertThat(dictionnaire.rimesDe("vert")).containsExactly("verre", "hiver");
    }

    @Test
    void reconnaitUneFlexionDuDepartMemeQuandUneListeThematiqueLaPropose() {
        DictionnaireDeRimes dictionnaire = new DictionnaireDeRimesPhonetique(List.of(
                entree("soirée", "a ʁ e", 90),
                entree("marée", "a ʁ e", 20),
                general("soirée", "s w a ʁ e", 90),
                new EntreePhonetique("soirées", List.of("s", "w", "a", "ʁ", "e"), 30, null, List.of("soirée")),
                general("entrée", "ɑ̃ t ʁ e", 50)), List.of());

        assertThat(dictionnaire.rimesDe("soirées")).containsExactly("entrée", "marée");
        assertThat(dictionnaire.rimesDe("soirées", ThemeDeRimes.SOIREE)).containsExactly("marée", "entrée");
    }

    @Test
    void confondLaLigatureOeEtSesDeuxLettres() {
        DictionnaireDeRimes dictionnaire = new DictionnaireDeRimesPhonetique(List.of(
                entree("cœur", "k œ ʁ", 50),
                general("coeur", "k œ ʁ", 220),
                general("sœur", "s œ ʁ", 150)), List.of());

        assertThat(dictionnaire.rimesDe("cœur")).containsExactly("sœur");
        assertThat(dictionnaire.rimesDe("soeur")).containsExactly("coeur");
    }

    private static EntreePhonetique entree(String mot, String sons, double frequence) {
        return new EntreePhonetique(mot, List.of(sons.split(" ")), frequence, ThemeDeRimes.SOIREE);
    }

    private static EntreePhonetique general(String mot, String sons, double frequence) {
        return new EntreePhonetique(mot, List.of(sons.split(" ")), frequence, null, List.of(mot));
    }
}
