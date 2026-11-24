package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChoixDeVersionParolesTest {
    private final ContenuKaraoke inscription = contenu(chanson(10, "Été", "Groupe", 180_000L, EtatParoles.SYNCHRONISEES));
    private final Source source = new Source();
    private final ChoixDeVersionParoles choix = new ChoixDeVersionParoles(source, new ProprietesSourceKaraoke(
            java.net.URI.create("https://example.invalid"), "Test", 5_000, 2_097_152, 120));

    @Test void choisitLaDureeLaPlusProchePuisLePlusPetitIdentifiant() {
        var preferee = chanson(2, "ete", "GROUPE", 198_000L, EtatParoles.SYNCHRONISEES);
        source.chansons = List.of(chanson(3, "Été", "Groupe", 202_000L, EtatParoles.SYNCHRONISEES), preferee,
                chanson(1, "Été", "Autre", 200_000L, EtatParoles.SYNCHRONISEES),
                chanson(4, "Été", "Groupe", 200_000L, EtatParoles.NON_SYNCHRONISEES),
                chanson(5, "Été", "Groupe", null, EtatParoles.SYNCHRONISEES));
        source.charge = Optional.of(contenu(preferee));
        assertThat(choix.choisir(inscription, 200_000)).isEqualTo(new ChoixDeVersionParoles.ResultatChoix(
                contenu(preferee), OrigineParoles.VERSION_ADAPTEE));
        assertThat(source.recherches).containsExactly("Été Groupe");
        assertThat(source.chargements).containsExactly(2L);
    }

    @Test void prefereLInscriptionEnCasDEgaliteSansRechargerSesParoles() {
        source.chansons = List.of(chanson(1, "Été", "Groupe", 200_000L, EtatParoles.SYNCHRONISEES));
        assertThat(choix.choisir(inscription, 190_000))
                .isEqualTo(new ChoixDeVersionParoles.ResultatChoix(inscription, OrigineParoles.VERSION_ADAPTEE));
        assertThat(source.chargements).isEmpty();
    }

    @Test void gardeLesParolesDAvantQuandLaVersionNestPasChargeable() {
        var proche = chanson(2, "Été", "Groupe", 200_000L, EtatParoles.SYNCHRONISEES);
        source.chansons = List.of(proche);
        for (var charge : List.of(Optional.<ContenuKaraoke>empty(),
                Optional.of(new ContenuKaraoke(proche, List.of(new LigneParoles(0, "  "), new LigneParoles(-1, "Texte")))),
                Optional.of(contenu(chanson(2, "Été", "Groupe", 0L, EtatParoles.SYNCHRONISEES))),
                Optional.of(contenu(chanson(2, "Été", "Groupe", 200_000L, EtatParoles.NON_SYNCHRONISEES))))) {
            source.charge = charge;
            assertThat(choix.choisir(inscription, 200_000))
                    .isEqualTo(new ChoixDeVersionParoles.ResultatChoix(inscription, OrigineParoles.REPLI_INSCRIPTION));
        }
    }

    @Test void uneDureeInconnueOuUneRechercheTropLongueNeContactentPasLaSource() {
        var longue = contenu(chanson(10, "été".repeat(50), "Groupe", 180_000L, EtatParoles.SYNCHRONISEES));
        assertThat(choix.choisir(inscription, 0).origine()).isEqualTo(OrigineParoles.REPLI_INSCRIPTION);
        assertThat(choix.choisir(longue, 180_000).contenu()).isEqualTo(longue);
        assertThat(source.recherches).isEmpty();
    }

    @Test void laPanneDeLaSourceNeFaitPasEchouerLaPreparation() {
        source.enPanne = true;
        assertThat(choix.choisir(inscription, 200_000))
                .isEqualTo(new ChoixDeVersionParoles.ResultatChoix(inscription, OrigineParoles.REPLI_INSCRIPTION));
    }

    private static Chanson chanson(long id, String titre, String artiste, Long duree, EtatParoles etat) {
        return new Chanson(IdentifiantsChanson.encoder(id), id, titre, artiste, null, duree, etat);
    }

    private static ContenuKaraoke contenu(Chanson chanson) {
        return new ContenuKaraoke(chanson, List.of(new LigneParoles(0, "Du texte")));
    }

    private static final class Source implements SourceDeParoles {
        List<Chanson> chansons = List.of();
        boolean enPanne;
        Optional<ContenuKaraoke> charge = Optional.empty();
        final List<String> recherches = new ArrayList<>();
        final List<Long> chargements = new ArrayList<>();

        @Override public List<Chanson> rechercher(String requete) {
            recherches.add(requete);
            if (enPanne) { throw new SourceParolesIndisponible("Source de test indisponible."); }
            return chansons;
        }

        @Override public Optional<ContenuKaraoke> charger(long id) {
            chargements.add(id);
            return charge;
        }
    }
}
