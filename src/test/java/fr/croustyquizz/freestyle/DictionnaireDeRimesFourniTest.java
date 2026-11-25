package fr.croustyquizz.freestyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DictionnaireDeRimesFourniTest {
    private static final String EXTRAIT = "/freestyle/lexique383-extrait.tsv";

    @Test
    void lExtraitDeLexiqueResteCompactEtChaqueLigneEstValide() throws IOException {
        byte[] contenu;
        try (InputStream flux = DictionnaireDeRimesFourniTest.class.getResourceAsStream(EXTRAIT)) {
            contenu = flux.readAllBytes();
        }
        long lignes = new String(contenu, StandardCharsets.UTF_8).lines().count();
        List<EntreePhonetique> entrees = entreesDeLExtrait();

        assertThat(contenu.length).isLessThan(2_000_000);
        assertThat(entrees).hasSize((int) lignes - 1).hasSizeGreaterThan(30_000)
                .allSatisfy(entree -> assertThat(entree.famille()).isNotEmpty());
    }

    @Test
    void seChargeEnQuelquesSecondesAuPlusPuisResteEnMemoire() {
        long debut = System.nanoTime();
        DictionnaireDeRimes dictionnaire = DictionnaireDeRimesFourni.charger();
        Duration duree = Duration.ofNanos(System.nanoTime() - debut);

        assertThat(dictionnaire.rimesDe("soirée")).isNotEmpty();
        assertThat(duree).isLessThan(Duration.ofSeconds(5));
        assertThat(DictionnaireDeRimesFourni.partage()).isSameAs(DictionnaireDeRimesFourni.partage());
    }

    @ParameterizedTest
    @CsvSource({
        "soirée, entrée",
        "soirée, préparé",
        "rap, cap",
        "micro, héros",
        "maison, raison",
        "nuit, aujourd'hui",
        "abricot, rigolo",
        "sœur, cœur",
        "soeur, cœur",
        "coeur, sœur"
    })
    void trouveLesRimesDUnMotQuelconqueDansLeLexiqueGeneral(String depart, String rime) {
        assertThat(DictionnaireDeRimesFourni.partage().rimesDe(depart)).contains(rime).doesNotContain(depart);
    }

    @Test
    void placeLesRimesRichesEnTeteEtTrieLeResteParFrequence() {
        List<String> rimes = DictionnaireDeRimesFourni.partage().rimesDe("soirée");

        assertThat(rimes).contains("préparé", "été", "bébé");
        assertThat(rimes.indexOf("préparé")).isLessThan(rimes.indexOf("été"));
        assertThat(rimes.indexOf("été")).isLessThan(rimes.indexOf("bébé"));
    }

    @Test
    void neProposePasLesFlexionsHomophonesDuMotDeDepart() {
        assertThat(DictionnaireDeRimesFourni.partage().rimesDe("maison"))
                .contains("raison", "saison").doesNotContain("maison", "maisons");
        assertThat(DictionnaireDeRimesFourni.partage().rimesDe("vert")).contains("verre", "hiver").doesNotContain("verts");
    }

    @ParameterizedTest
    @CsvSource({
        "soirées, soirée",
        "chansons, chanson",
        "ballons, ballon",
        "passages, passage",
        "villages, village",
        "lumières, lumière",
        "actions, action"
    })
    void neProposeJamaisUneFlexionHomophoneQueDonneUneListeThematique(String depart, String flexion) {
        DictionnaireDeRimes dictionnaire = DictionnaireDeRimesFourni.partage();

        assertThat(dictionnaire.rimesDe(depart)).isNotEmpty().doesNotContain(flexion);
        for (ThemeDeRimes theme : ThemeDeRimes.values()) {
            assertThat(dictionnaire.rimesDe(depart, theme)).as("thème %s", theme)
                    .isNotEmpty().doesNotContain(flexion);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "bonne, conne",
        "bonne, connes",
        "partout, fout",
        "galope, salopes",
        "chute, putes",
        "matin, putains",
        "fouille, couilles",
        "chanté, emmerdé",
        "chanté, enculés",
        "chanté, pissé",
        "jamais, emmerdait",
        "jamais, chiais",
        "regard, bâtards",
        "regard, connards",
        "heureux, merdeux",
        "tasse, pétasses",
        "soirée, enfoirés",
        "chanté, pédés",
        "fraise, baisent",
        "carottes, chiottes",
        "chanté, démerder",
        "soir, foutoir"
    })
    void leFiltreEcarteAussiLesFormesFlechiesVenuesDeLexique(String depart, String formeFiltree) throws IOException {
        DictionnaireDeRimes dictionnaire = DictionnaireDeRimesFourni.partage();

        assertThat(entreesDeLExtrait()).extracting(EntreePhonetique::mot).contains(formeFiltree);
        assertThat(dictionnaire.rimesDe(depart)).isNotEmpty().doesNotContain(formeFiltree);
        assertThat(dictionnaire.rimesDe(formeFiltree)).isEmpty();
    }

    private static List<EntreePhonetique> entreesDeLExtrait() throws IOException {
        try (BufferedReader lecteur = new BufferedReader(new InputStreamReader(
                DictionnaireDeRimesFourniTest.class.getResourceAsStream(EXTRAIT), StandardCharsets.UTF_8))) {
            return LexiqueDeRimes.lire(lecteur, EXTRAIT);
        }
    }
}
