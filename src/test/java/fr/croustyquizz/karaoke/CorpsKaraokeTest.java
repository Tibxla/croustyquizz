package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.croustyquizz.manche.RefusDeCommande;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Validation et clé de regroupement des corps du Karaoké, sans base ni HTTP. */
class CorpsKaraokeTest {
    private static String cle(String texte) {
        return CorpsKaraoke.normaliserSuggestion(CorpsKaraoke.nettoyerSuggestion(texte));
    }

    private static void refuser(String titre) {
        assertThatThrownBy(() -> new CorpsKaraoke.SuggestionChanson(titre, "Indochine"))
                .isInstanceOfSatisfying(RefusDeCommande.class,
                        refus -> assertThat(refus.getMessage()).contains("invalide"));
    }

    @Test void laLongueurSeRecompteApresNfc() {
        String court = "\uFB2C".repeat(120);
        assertThat(court.codePointCount(0, court.length())).isEqualTo(120);
        refuser(court);
        assertThatCode(() -> new CorpsKaraoke.SuggestionChanson("É".repeat(120), "Indochine")).doesNotThrowAnyException();
    }

    @Test void lesApostrophesEtGuillemetsTypographiquesRejoignentLeurFormeAscii() {
        assertThat(cle("J’ai demandé à la lune")).isEqualTo(cle("J'ai demandé à la lune"));
        assertThat(cle("« Lune »")).isEqualTo(cle("\"Lune\"")).isEqualTo(cle("“Lune”")).isEqualTo(cle("Lune"));
        assertThat(cle("‘Lune’")).isEqualTo(cle("'Lune'"));
    }

    @Test void laCleAppliqueNfkcEtIgnoreLaPonctuationSansToucherAuxLettres() {
        assertThat(cle("ﬁn ＡＢ")).isEqualTo(cle("fin ab"));
        assertThat(cle("A - B")).isEqualTo(cle("A B")).isEqualTo("a b");
        assertThat(cle("Éclat, du soir !")).isEqualTo(cle("eclat du soir"));
        assertThat(cle("Lune")).isNotEqualTo(cle("Lunes"));
    }

    @Test void laCleConserveLesMarquesHorsDeLaPlageDesDiacritiquesRetires() {
        assertThat(cle("がっこう")).isEqualTo(cle("か\u3099っこう")).isNotEqualTo(cle("かっこう"));
        assertThat(cle("का")).isNotEqualTo(cle("क"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u200B", "Lune\u200B", "\u00AD", "\uFEFFLune", "Lu\u0007ne", "!!!", "« … »", ""})
    void refuseLesCaracteresDeFormatEtLesClesVides(String titre) {
        refuser(titre);
    }

    /** Un cas de src/test/resources/karaoke/verdicts-suggestion.json, relu par le test de la Manette. */
    record Verdict(String texte, Integer repetitions, boolean accepte) {
        String valeur() { return texte.repeat(repetitions == null ? 1 : repetitions); }
    }

    record Verdicts(String description, List<Verdict> cas) { }

    static List<Verdict> verdictsPartages() throws IOException {
        try (InputStream flux = CorpsKaraokeTest.class.getResourceAsStream("/karaoke/verdicts-suggestion.json")) {
            return JsonMapper.builder().build().readValue(flux, Verdicts.class).cas();
        }
    }

    @ParameterizedTest
    @MethodSource("verdictsPartages")
    void lesVerdictsPartagesAvecLaManetteSontCeuxDuServeur(Verdict verdict) {
        for (var corps : List.<Runnable>of(() -> new CorpsKaraoke.SuggestionChanson(verdict.valeur(), "Indochine"),
                () -> new CorpsKaraoke.SuggestionChanson("Lune", verdict.valeur()))) {
            boolean accepte;
            try { corps.run(); accepte = true; } catch (RefusDeCommande refus) { accepte = false; }
            assertThat(accepte).as("%s", verdict).isEqualTo(verdict.accepte());
        }
    }

    @Test void lesTabulationsRestentDesBlancs() {
        assertThatCode(() -> new CorpsKaraoke.SuggestionChanson("Éclat\tdu\nsoir", "Les Accords")).doesNotThrowAnyException();
        assertThat(cle("Éclat\tdu\nsoir")).isEqualTo(cle("Éclat du soir"));
    }

    @Test void lesTroisCorpsDeSuggestionEtDeFavoriRefusentUneCleInconnueMemeHorsDuConvertisseur() {
        var json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        assertThatThrownBy(() -> json.readValue("{\"titre\":\"Lune\",\"artiste\":\"Indochine\",\"pseudo\":\"Jo\"}",
                CorpsKaraoke.SuggestionChanson.class)).hasRootCauseInstanceOf(RefusDeCommande.class);
        assertThatThrownBy(() -> json.readValue("{\"nombreJoueurs\":1,\"pseudo\":\"Jo\"}",
                CorpsKaraoke.RetraitSuggestionChanson.class)).hasRootCauseInstanceOf(RefusDeCommande.class);
        assertThatThrownBy(() -> json.readValue("{\"favorite\":true,\"lieuId\":\"x\"}",
                CorpsKaraoke.ChoixFavoriChanson.class)).hasRootCauseInstanceOf(RefusDeCommande.class);
    }
}
