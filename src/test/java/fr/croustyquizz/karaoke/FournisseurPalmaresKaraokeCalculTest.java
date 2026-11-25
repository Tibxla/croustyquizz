package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.Destination;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.ModeDeJeu;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Coût du Palmarès dans un assemblage : un calcul pour la salle et la Console, aucun pendant une Manche active. */
class FournisseurPalmaresKaraokeCalculTest {
    private final UUID soiree = UUID.randomUUID();
    private final DepotDeManches depot = mock(DepotDeManches.class);
    private final ServiceKaraoke service = mock(ServiceKaraoke.class);
    private final FournisseurPalmaresKaraoke fournisseur;

    FournisseurPalmaresKaraokeCalculTest() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("serviceKaraoke", service);
        fournisseur = new FournisseurPalmaresKaraoke(depot, beans.getBeanProvider(ServiceKaraoke.class));
    }

    @Test void calculeLePalmaresUneSeuleFoisPourLaSalleEtLaConsole() {
        var manche = new ManchePersistante(UUID.randomUUID(), soiree, ModeDeJeu.KARAOKE, "{}", Instant.EPOCH);
        manche.changerEtat(EtatManche.TERMINEE, Instant.EPOCH.plusSeconds(60));
        when(depot.deLaSoiree(soiree)).thenReturn(List.of(manche));
        when(service.lecture(any(), any())).thenAnswer(appel -> appel.<Supplier<?>>getArgument(1).get());
        var lignes = List.of(ligne(1L), ligne(2L), ligne(3L), ligne(4L), ligne(null));
        when(service.composerPalmares(soiree, List.of(manche))).thenReturn(lignes);

        var prepare = fournisseur.preparer(soiree);
        Map<String, Object> partie = new java.util.HashMap<>();
        partie.put("mancheActive", null);
        assertThat(prepare.fournir(Destination.CONSOLE, partie)).isEqualTo(lignes);
        assertThat(prepare.fournir(Destination.SALLE, partie)).isEqualTo(lignes.subList(0, 3));

        verify(depot, times(1)).deLaSoiree(soiree);
        verify(service, times(1)).composerPalmares(soiree, List.of(manche));
    }

    @Test void neLitPasLaBasePendantUneMancheActive() {
        var prepare = fournisseur.preparer(soiree);
        Map<String, Object> partie = Map.of("mancheActive", Map.of("etat", "EN_COURS"));

        assertThat(prepare.fournir(Destination.SALLE, partie)).isNull();
        assertThat(prepare.fournir(Destination.CONSOLE, partie)).isNull();
        verifyNoInteractions(depot, service);
    }

    private static VuesKaraoke.LignePalmaresKaraoke ligne(Long rang) {
        return new VuesKaraoke.LignePalmaresKaraoke(UUID.randomUUID(), rang == null ? 9 : rang, rang, null, List.of(), 0, null);
    }
}
