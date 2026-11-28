package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Le catalogue de la Bibliothèque de base passe avant LRCLIB et lui survit. */
class SourcesDeChansonsTest {
    private final CatalogueDeBase catalogue = CatalogueDeBaseTest.essai();
    private final Lrclib lrclib = new Lrclib();
    private final SourcesDeChansons sources = new SourcesDeChansons(catalogue, lrclib);

    @Test
    void laRechercheProposeLeCatalogueAvantLrclib() {
        assertThat(sources.rechercher("lanterne lucioles")).extracting(Chanson::chansonId)
                .containsExactly(IdentifiantsChanson.encoderBibliotheque(1), IdentifiantsChanson.encoder(7));
        assertThat(lrclib.recherches).containsExactly("lanterne lucioles");
        assertThat(sources.rechercher("traversée")).extracting(Chanson::chansonId)
                .containsExactly(IdentifiantsChanson.encoder(7));
    }

    @Test
    void laRechercheSePasseDeLrclibEnPanneQuandLeCatalogueATrouve() {
        lrclib.enPanne = true;
        assertThat(sources.rechercher("Lanterne")).extracting(Chanson::chansonId)
                .containsExactly(IdentifiantsChanson.encoderBibliotheque(1));
        assertThatExceptionOfType(SourceParolesIndisponible.class).isThrownBy(() -> sources.rechercher("traversée"));
    }

    @Test
    void uneChansonDuCatalogueSeChargeSansJamaisConsulterLrclib() {
        lrclib.enPanne = true;
        var contenu = sources.charger(IdentifiantsChanson.encoderBibliotheque(1)).orElseThrow();
        assertThat(contenu).isEqualTo(catalogue.contenu(1).orElseThrow());
        assertThat(sources.charger(IdentifiantsChanson.encoderBibliotheque(2))).isEmpty();
        assertThat(lrclib.chargements).isEmpty();
    }

    @Test
    void uneChansonDeLrclibSeChargeChezLrclibEtUnIdentifiantInconnuNullePart() {
        assertThat(sources.charger(IdentifiantsChanson.encoder(1)).orElseThrow().chanson().chansonId())
                .isEqualTo(IdentifiantsChanson.encoder(1));
        assertThat(sources.charger("indécodable")).isEmpty();
        assertThat(lrclib.chargements).containsExactly(1L);
        lrclib.enPanne = true;
        assertThatExceptionOfType(SourceParolesIndisponible.class)
                .isThrownBy(() -> sources.charger(IdentifiantsChanson.encoder(1)));
    }

    private static final class Lrclib implements SourceDeParoles {
        boolean enPanne;
        final List<String> recherches = new ArrayList<>();
        final List<Long> chargements = new ArrayList<>();

        @Override public List<Chanson> rechercher(String recherche) {
            recherches.add(recherche);
            if (enPanne) { throw new SourceParolesIndisponible("Panne factice"); }
            return List.of(new Chanson(IdentifiantsChanson.encoder(7), 7, "La traversée", "Les Lucioles", null, 1000L,
                    EtatParoles.SYNCHRONISEES));
        }

        @Override public Optional<ContenuKaraoke> charger(long idLrclib) {
            chargements.add(idLrclib);
            if (enPanne) { throw new SourceParolesIndisponible("Panne factice"); }
            return Optional.of(ServiceKaraokeTest.contenu(idLrclib));
        }
    }
}
