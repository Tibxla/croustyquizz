package fr.croustyquizz.freestyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CompositeurDeGrilleTest {
    private static final UUID INSTRUMENTALE_ID = UUID.fromString("70000000-0000-4000-8000-000000000004");

    @Test
    void placeLesRimesAbabSurLeTroisiemeTempsSansCumulerLesArrondis() {
        var grille = new CompositeurDeGrille(new DictionnaireDeRimesFactice())
                .composer(reglages(SchemaDeRimes.ABAB, 8), new Instrumentale(INSTRUMENTALE_ID, "À la lueur", 92, 250),
                        DictionnaireDeRimesFactice.MOTS_DE_VILLE)
                .orElseThrow();

        assertThat(grille.mots()).extracting(GrilleDeRimes.Mot::mot)
                .containsExactly("lumière", "nuit", "pierre", "bruit", "rivière", "luit", "poussière", "minuit");
        assertThat(grille.mots()).extracting(GrilleDeRimes.Mot::mesure).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(grille.mots()).extracting(GrilleDeRimes.Mot::temps).containsOnly(3);
        assertThat(grille.mots()).extracting(GrilleDeRimes.Mot::positionMs)
                .containsExactly(1554L, 4163L, 6772L, 9380L, 11989L, 14598L, 17207L, 19815L);
    }

    @ParameterizedTest
    @CsvSource({"TOUS, 1-2-3-4-5-6-7-8", "PREMIER, 1-3", "DERNIER, 6-8", "AUCUN, aucun"})
    void masqueSelonLaPremiereOuDerniereOccurrenceDeChaqueFamilleSurTouteLaGrille(
            DifficulteFreestyle difficulte, String mesuresVisibles) {
        var grille = new CompositeurDeGrille(new DictionnaireDeRimesFactice())
                .composer(reglages(SchemaDeRimes.AABB, 8), new Instrumentale(INSTRUMENTALE_ID, "À la lueur", 120, 0),
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
    @CsvSource({"16, 29760, naufrage", "32, 60480, hommage"})
    void composeUneSeuleFamilleSansRepeterDeMotPourLesGrillesLongues(
            int nombreMesures, long dernierePosition, String dernierMot) {
        var reglages = new ReglagesFreestyle(ThemeDeRimes.SOIREE, SchemaDeRimes.AAAA, DifficulteFreestyle.DERNIER,
                nombreMesures, List.of(INSTRUMENTALE_ID));
        var grille = new CompositeurDeGrille(new DictionnaireDeRimesFactice())
                .composer(reglages, new Instrumentale(INSTRUMENTALE_ID, "Départ", 125, 0), List.of("voyage")).orElseThrow();

        assertThat(grille.mots()).hasSize(nombreMesures).extracting(GrilleDeRimes.Mot::mot).doesNotHaveDuplicates();
        assertThat(grille.mots().getLast().positionMs()).isEqualTo(dernierePosition);
        assertThat(reglages.difficulte().appliquer(grille).mots().stream().map(GrilleDeRimes.Mot::mot)
                .filter(Objects::nonNull)).containsExactly(dernierMot);
    }

    private static ReglagesFreestyle reglages(SchemaDeRimes schema, int nombreMesures) {
        return new ReglagesFreestyle(ThemeDeRimes.VILLE, schema, DifficulteFreestyle.PREMIER, nombreMesures, List.of(INSTRUMENTALE_ID));
    }
}
