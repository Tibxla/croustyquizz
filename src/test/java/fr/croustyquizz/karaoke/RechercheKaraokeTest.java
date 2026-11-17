package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RechercheKaraokeTest {
    @Test
    void classeParEgalitePuisPrefixesEtDedoublonneAvantClassement() {
        Chanson premiere = chanson(5, "Zéro", "Autre");
        Chanson egaliteTitre = chanson(4, "ÉTÉ", "Les Néons");
        Chanson egaliteArtiste = chanson(3, "Avril", "été");
        Chanson prefixe = chanson(2, "Étendue", "Les Néons");
        Chanson sansCorrespondance = chanson(1, "Ailleurs", "Les Néons");
        List<Chanson> source = List.of(premiere, prefixe, egaliteTitre, sansCorrespondance,
                egaliteArtiste, chanson(5, "été", "Doublon"));
        List<Chanson> resultat = RechercheKaraoke.classer("ete", source);
        assertThat(resultat).containsExactly(egaliteArtiste, egaliteTitre, prefixe, sansCorrespondance, premiere);
        assertThat(source).hasSize(6);
        org.assertj.core.api.Assertions.assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> resultat.add(premiere));
        assertThat(RechercheKaraoke.classer("les eten", List.of(sansCorrespondance, prefixe)))
                .containsExactly(prefixe, sansCorrespondance);
    }

    @Test
    void normaliseLesLigaturesMarquesChiffresEtFormesDeCompatibilite() {
        Chanson ligatures = chanson(1, "CŒUR, Æther!", "Artiste");
        Chanson compatibilite = chanson(2, "Ｆｏｏ ①", "Artiste");
        assertThat(RechercheKaraoke.classer("coeur ae", List.of(compatibilite, ligatures)))
                .containsExactly(ligatures, compatibilite);
        assertThat(RechercheKaraoke.classer("foo 1", List.of(ligatures, compatibilite)))
                .containsExactly(compatibilite, ligatures);
        Chanson marques = chanson(3, "a\u1ab0\u20dd\ufe20", "Artiste");
        assertThat(RechercheKaraoke.classer("a", List.of(ligatures, marques))).startsWith(marques);
    }

    @Test
    void compareLesPointsDeCodePourChaqueCritereEtDepartageParIdentifiant() {
        Chanson bmp = chanson(1, "\ufa0e", "Artiste");
        Chanson supplementaire = chanson(2, "𐐀", "Artiste");
        assertThat(RechercheKaraoke.classer("absent", List.of(supplementaire, bmp)))
                .containsExactly(bmp, supplementaire);
        Chanson artisteBmp = chanson(3, "Titre", "\ufa0e");
        Chanson artisteSup = chanson(4, "Titre", "𐐀");
        assertThat(RechercheKaraoke.classer("absent", List.of(artisteSup, artisteBmp)))
                .containsExactly(artisteBmp, artisteSup);
        Chanson premierId = chanson(1, "Titre", "Artiste");
        Chanson secondId = chanson(2, "Titre", "Artiste");
        assertThat(RechercheKaraoke.classer("titre", List.of(premierId, secondId)))
                .containsExactly(secondId, premierId);
    }

    private static Chanson chanson(long id, String titre, String artiste) {
        return new Chanson(IdentifiantsChanson.encoder(id), id, titre, artiste, null, null, EtatParoles.INCONNU);
    }

    @Test
    void nettoieLesEspacesUnicodeEtNormaliseSansCompterLesUnitesUtf16() {
        assertThat(RechercheKaraoke.nettoyer("\u0085\u00a0E\u0301te\u2007\u202f\u3000𐐀\t", 6))
                .isEqualTo("Éte 𐐀");
        assertThat(RechercheKaraoke.nettoyer("𐐀".repeat(120), 120)).isEqualTo("𐐀".repeat(120));
        assertThat(RechercheKaraoke.nettoyer("四", 1)).isEqualTo("四");
        assertThat(RechercheKaraoke.nettoyer("Ⅳ", 1)).isEqualTo("Ⅳ");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \u00a0\u0085", "?! 🎤", "\u0301", "\u200b"})
    void refuseUneRechercheSansLettreNiChiffre(String saisie) {
        assertThatIllegalArgumentException().isThrownBy(() -> RechercheKaraoke.nettoyer(saisie, 120));
    }

    @Test
    void verifieLesBornesBrutesPuisLaLimiteReduiteSansTronquer() {
        assertThatIllegalArgumentException().isThrownBy(() -> RechercheKaraoke.nettoyer("a".repeat(121), 120));
        assertThatIllegalArgumentException().isThrownBy(() -> RechercheKaraoke.nettoyer(" ".repeat(120) + "a", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> RechercheKaraoke.nettoyer("été", 2));
        assertThatIllegalArgumentException().isThrownBy(() -> RechercheKaraoke.nettoyer("a", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> RechercheKaraoke.nettoyer("a", 121));
        assertThat(RechercheKaraoke.nettoyer(" a ", 1)).isEqualTo("a");
    }
}
