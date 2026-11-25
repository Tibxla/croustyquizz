package fr.croustyquizz.freestyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CompositeurDeGrilleTest {
    private static final UUID INSTRUMENTALE_ID = UUID.fromString("70000000-0000-4000-8000-000000000004");
    private static final List<String> RIMES_EN_IERE = List.of("lumière", "pierre", "rivière", "poussière", "frontière",
            "clairière", "verrière", "ornière");
    private static final List<String> RIMES_EN_UIT = List.of("nuit", "bruit", "luit", "minuit", "ennui", "pluie", "fruit", "circuit");

    @Test
    void placeLesRimesAbabSurLeTroisiemeTempsSansCumulerLesArrondis() {
        var grille = compositeur(70)
                .composer(reglages(SchemaDeRimes.ABAB, 8), new Instrumentale(INSTRUMENTALE_ID, "À la lueur", 92, 250, 60_000),
                        DictionnaireDeRimesFactice.MOTS_DE_VILLE)
                .orElseThrow();

        var mots = grille.mots().stream().map(GrilleDeRimes.Mot::mot).toList();
        var familleA = List.of(mots.get(0), mots.get(2), mots.get(4), mots.get(6));
        var familleB = List.of(mots.get(1), mots.get(3), mots.get(5), mots.get(7));
        assertThat(mots).doesNotHaveDuplicates();
        assertThat(List.of(familleA, familleB)).anySatisfy(famille -> assertThat(RIMES_EN_IERE).containsAll(famille))
                .anySatisfy(famille -> assertThat(RIMES_EN_UIT).containsAll(famille));
        assertThat(grille.mots()).extracting(GrilleDeRimes.Mot::mesure).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(grille.mots()).extracting(GrilleDeRimes.Mot::temps).containsOnly(3);
        assertThat(grille.mots()).extracting(GrilleDeRimes.Mot::positionMs)
                .containsExactly(1554L, 4163L, 6772L, 9380L, 11989L, 14598L, 17207L, 19815L);
    }

    @Test
    void melangeLesDepartsEtLesRimesAChaqueGrilleDeFaconReproductible() {
        var reglages = reglages(SchemaDeRimes.ABAB, 8);
        var instrumentale = new Instrumentale(INSTRUMENTALE_ID, "À la lueur", 120, 0, 60_000);
        var compositeur = compositeur(70);

        var premiere = compositeur.composer(reglages, instrumentale, DictionnaireDeRimesFactice.MOTS_DE_VILLE).orElseThrow();
        var seconde = compositeur.composer(reglages, instrumentale, DictionnaireDeRimesFactice.MOTS_DE_VILLE).orElseThrow();

        assertThat(seconde.mots()).extracting(GrilleDeRimes.Mot::mot)
                .isNotEqualTo(premiere.mots().stream().map(GrilleDeRimes.Mot::mot).toList());
        assertThat(compositeur(70).composer(reglages, instrumentale, DictionnaireDeRimesFactice.MOTS_DE_VILLE))
                .contains(premiere);
    }

    @ParameterizedTest
    @CsvSource({"TOUS, 1-2-3-4-5-6-7-8", "PREMIER, 1-3", "DERNIER, 6-8", "AUCUN, aucun"})
    void masqueSelonLaPremiereOuDerniereOccurrenceDeChaqueFamilleSurTouteLaGrille(
            DifficulteFreestyle difficulte, String mesuresVisibles) {
        var grille = compositeur(70)
                .composer(reglages(SchemaDeRimes.AABB, 8), new Instrumentale(INSTRUMENTALE_ID, "À la lueur", 120, 0, 60_000),
                        DictionnaireDeRimesFactice.MOTS_DE_VILLE)
                .orElseThrow();

        GrilleDeRimes projection = difficulte.appliquer(grille);

        var visibles = projection.mots().stream().filter(mot -> mot.mot() != null)
                .map(GrilleDeRimes.Mot::mesure).toList();
        assertThat(visibles).containsExactlyElementsOf(mesuresVisibles.equals("aucun") ? List.of()
                : Arrays.stream(mesuresVisibles.split("-")).map(Integer::valueOf).toList());
        assertThat(projection.mots()).extracting(GrilleDeRimes.Mot::famille)
                .containsExactly(SchemaDeRimes.Famille.A, SchemaDeRimes.Famille.A, SchemaDeRimes.Famille.B,
                        SchemaDeRimes.Famille.B, SchemaDeRimes.Famille.A, SchemaDeRimes.Famille.A,
                        SchemaDeRimes.Famille.B, SchemaDeRimes.Famille.B);
        assertThat(projection.mots().getLast().positionMs()).isEqualTo(15000);
        assertThat(grille.mots()).extracting(GrilleDeRimes.Mot::mot).doesNotContainNull();
    }

    @ParameterizedTest
    @CsvSource({"16, 29760", "32, 60480"})
    void composeUneSeuleFamilleSansRepeterDeMotPourLesGrillesLongues(int nombreMesures, long dernierePosition) {
        var reglages = new ReglagesFreestyle(ThemeDeRimes.SOIREE, SchemaDeRimes.AAAA, DifficulteFreestyle.DERNIER,
                nombreMesures, List.of(INSTRUMENTALE_ID));
        var grille = compositeur(70)
                .composer(reglages, new Instrumentale(INSTRUMENTALE_ID, "Départ", 125, 0, 90_000), List.of("voyage")).orElseThrow();

        assertThat(grille.mots()).hasSize(nombreMesures).extracting(GrilleDeRimes.Mot::mot).doesNotHaveDuplicates();
        assertThat(grille.mots().getLast().positionMs()).isEqualTo(dernierePosition);
        assertThat(reglages.difficulte().appliquer(grille).mots().stream().map(GrilleDeRimes.Mot::mot)
                .filter(Objects::nonNull)).containsExactly(grille.mots().getLast().mot());
    }

    @Test
    void neComposeRienSansFamilleAssezLongue() {
        var reglages = new ReglagesFreestyle(ThemeDeRimes.SPORT, SchemaDeRimes.AAAA, DifficulteFreestyle.TOUS, 32,
                List.of(INSTRUMENTALE_ID));
        var instrumentale = new Instrumentale(INSTRUMENTALE_ID, "Départ", 125, 0, 90_000);
        var dictionnaire = new DictionnaireDeRimesFactice();

        assertThat(new CompositeurDeGrille(dictionnaire, new Random(70))
                .composer(reglages, instrumentale, dictionnaire.amorces(ThemeDeRimes.SPORT))).isEmpty();
    }

    private static CompositeurDeGrille compositeur(long graine) {
        return new CompositeurDeGrille(new DictionnaireDeRimesFactice(), new Random(graine));
    }

    private static ReglagesFreestyle reglages(SchemaDeRimes schema, int nombreMesures) {
        return new ReglagesFreestyle(ThemeDeRimes.VILLE, schema, DifficulteFreestyle.PREMIER, nombreMesures, List.of(INSTRUMENTALE_ID));
    }
}
