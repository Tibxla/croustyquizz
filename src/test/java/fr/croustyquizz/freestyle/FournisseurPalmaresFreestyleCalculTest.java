package fr.croustyquizz.freestyle;

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
import fr.croustyquizz.passage.ParticipantsDePassage;
import fr.croustyquizz.passage.PersistancePassageJpa;
import fr.croustyquizz.passage.ResultatVote;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Coût et robustesse du Palmarès du Freestyle dans un assemblage de vues. */
class FournisseurPalmaresFreestyleCalculTest {
    private final UUID soiree = UUID.randomUUID();
    private final UUID joueur = UUID.randomUUID();
    private final DepotDeManches depot = mock(DepotDeManches.class);
    private final ServiceFreestyle service = mock(ServiceFreestyle.class);
    private final PersistanceFreestyleJpa persistance = mock(PersistanceFreestyleJpa.class);
    private final PersistancePassageJpa passages = mock(PersistancePassageJpa.class);
    private final ParticipantsDePassage participants = mock(ParticipantsDePassage.class);
    private final ManchePersistante manche = new ManchePersistante(UUID.randomUUID(), soiree, ModeDeJeu.FREESTYLE, "{}", Instant.EPOCH);
    private final FournisseurPalmaresFreestyle fournisseur;

    FournisseurPalmaresFreestyleCalculTest() {
        var beans = new StaticListableBeanFactory();
        beans.addBean("serviceFreestyle", service);
        fournisseur = new FournisseurPalmaresFreestyle(depot, beans.getBeanProvider(ServiceFreestyle.class),
                persistance, passages, participants);
        manche.changerEtat(EtatManche.TERMINEE, Instant.EPOCH.plusSeconds(60));
        when(depot.deLaSoiree(soiree)).thenReturn(List.of(manche));
        when(service.lecture(any(), any())).thenAnswer(appel -> appel.<Supplier<?>>getArgument(1).get());
        when(participants.pseudos(soiree)).thenReturn(Map.of(joueur, "Léa"));
    }

    @Test void calculeLePalmaresUneSeuleFoisPourLaSalleEtLaConsole() {
        // Cinq Passages notés et un sans vote : la salle n'en montre que trois, la Console les garde tous.
        var resultats = List.of(resultat(1, 5), resultat(2, 4), resultat(3, 3), resultat(4, 2), resultat(5, 1), resultat(6, 0));
        avec(resultats, contenus(resultats));

        var prepare = fournisseur.preparer(soiree);
        var salle = (List<?>) prepare.fournir(Destination.SALLE, horsManche());
        var console = (List<?>) prepare.fournir(Destination.CONSOLE, horsManche());

        assertThat(numeros(salle)).containsExactly(1L, 2L, 3L);
        assertThat(numeros(console)).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        verify(service, times(1)).lecture(any(), any());
        verify(depot, times(1)).deLaSoiree(soiree);
        verify(passages, times(1)).resultatsDeVote(List.of(manche.getMancheId()));
        verify(persistance, times(1)).contenus(manche.getMancheId());
        verify(participants, times(1)).pseudos(soiree);
    }

    @Test void neLitPasLaBasePendantUneMancheActive() {
        var prepare = fournisseur.preparer(soiree);
        Map<String, Object> partie = Map.of("mancheActive", Map.of("etat", "EN_COURS"));

        assertThat(prepare.fournir(Destination.SALLE, partie)).isNull();
        assertThat(prepare.fournir(Destination.CONSOLE, partie)).isNull();
        verifyNoInteractions(depot, service, persistance, passages, participants);
    }

    @Test void ecarteUnPassageSansContenuFigeSansCasserLesRangs() {
        var resultats = List.of(resultat(1, 3), resultat(2, 5), resultat(3, 4));
        var contenus = contenus(resultats);
        contenus.remove(resultats.get(2).passageId());
        avec(resultats, contenus);

        var prepare = fournisseur.preparer(soiree);
        var console = (List<?>) prepare.fournir(Destination.CONSOLE, horsManche());
        var salle = (List<?>) prepare.fournir(Destination.SALLE, horsManche());

        assertThat(numeros(console)).containsExactly(2L, 1L);
        assertThat(console).extracting("rang").containsExactly(1L, 2L);
        assertThat(salle).isEqualTo(console);
    }

    private void avec(List<ResultatVote> resultats, Map<UUID, EtatFreestyle.Contenu> contenus) {
        when(passages.resultatsDeVote(List.of(manche.getMancheId()))).thenReturn(resultats);
        when(persistance.contenus(manche.getMancheId())).thenReturn(contenus);
    }

    private ResultatVote resultat(long numero, int note) {
        long votes = note == 0 ? 0 : 1;
        return new ResultatVote(UUID.randomUUID(), manche.getMancheId(), numero, List.of(joueur), votes, note,
                note == 0 ? null : note * 10);
    }

    private static Map<UUID, EtatFreestyle.Contenu> contenus(List<ResultatVote> resultats) {
        Map<UUID, EtatFreestyle.Contenu> contenus = new HashMap<>();
        resultats.forEach(resultat -> contenus.put(resultat.passageId(), new EtatFreestyle.Contenu(null, null)));
        return contenus;
    }

    private static Map<String, Object> horsManche() {
        Map<String, Object> partie = new HashMap<>();
        partie.put("mancheActive", null);
        return partie;
    }

    private static List<Long> numeros(List<?> lignes) {
        return lignes.stream().map(ligne -> ((VuesFreestyle.LignePalmares) ligne).numero()).toList();
    }
}
