package fr.croustyquizz.passage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class FileAttenteCollectiveTest {
    private static final UUID SOIREE = UUID.randomUUID();
    private static final UUID MANCHE = UUID.randomUUID();
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BORIS = UUID.randomUUID();
    private static final UUID CELIA = UUID.randomUUID();
    private static final UUID DINA = UUID.randomUUID();
    private static final Instant RECU = Instant.parse("2026-11-24T12:00:00Z");
    private final FileAttente file = FileAttente.depuis(new EtatFileAttente(
            SOIREE, MANCHE, 10, 1, 1, 1, null, List.of(), Map.of()));
    private long numero;

    @Test
    void inscritJusquaQuatreInterpretesAuTourLePlusTardifEtReserveCeTourPourChacun() {
        inscrire("Boris 1", BORIS);
        inscrire("Boris 2", BORIS);
        UUID groupe = inscrire("Groupe", ANA, BORIS, CELIA, DINA);
        UUID ana = inscrire("Ana", ANA);
        UUID celia = inscrire("Célia", CELIA);
        UUID dina = inscrire("Dina", DINA);

        assertThat(file.trouver(groupe).interpretes()).containsExactly(ANA, BORIS, CELIA, DINA);
        assertThat(file.trouver(groupe).tourNormal()).isEqualTo(3);
        assertThat(file.ordreEffectif().subList(3, 6)).extracting(Passage::id).containsExactly(ana, celia, dina);
        assertThat(file.ordreEffectif()).extracting(Passage::tourNormal).containsExactly(1L, 2L, 3L, 4L, 4L, 4L);
        refuserSansMutation(() -> file.ajouterInterprete(cible(groupe), UUID.randomUUID()), "DONNEES_INVALIDES");
    }

    @Test
    void ajouterUnInterpretePeutFaireReculerLePassageEtPerimeSonAncienneCible() {
        UUID boris = inscrire("Boris", BORIS);
        UUID duo = inscrire("Duo", ANA);
        UUID celia = inscrire("Célia", CELIA);
        UUID ana = inscrire("Ana", ANA);
        var ancienneCible = cible(duo);

        assertThat(file.ajouterInterprete(ancienneCible, BORIS)).isInstanceOf(ResultatFile.Modifiee.class);

        // Seul le Passage accepté recule : l’autre attente d’Ana garde son Tour 2 et son compteur (#135).
        assertThat(file.ordreEffectif()).extracting(Passage::id).containsExactly(boris, celia, ana, duo);
        assertThat(file.ordreEffectif()).extracting(Passage::tourNormal).containsExactly(1L, 1L, 2L, 3L);
        assertThat(file.trouver(duo).interpretes()).containsExactly(ANA, BORIS);
        assertThat(file.trouver(duo).numeroModification()).isEqualTo(1);
        assertThat(file.trouver(ana).numeroModification()).isZero();
        refuserSansMutation(() -> file.ajouterInterprete(ancienneCible, CELIA), "COMMANDE_PERIMEE");
        assertThat(file.ajouterInterprete(cible(duo), DINA)).isInstanceOf(ResultatFile.Modifiee.class);
        assertThat(file.trouver(duo).tourNormal()).isEqualTo(3);
        assertThat(file.trouver(duo).numeroModification()).isEqualTo(2);
    }

    @Test
    void laPrioriteConsommeLePremierTourDisponibleDeChaqueInterprete() {
        inscrire("Ana 1", ANA);
        file.extraireProchainPassage(false);
        UUID ana = inscrire("Ana 2", ANA);
        UUID boris = inscrire("Boris", BORIS);
        UUID celia = inscrire("Célia", CELIA);
        UUID groupe = inscrire("Groupe", ANA, BORIS);
        assertThat(file.trouver(groupe).tourNormal()).isEqualTo(3);

        file.definirPriorite(cible(groupe), true);
        Passage appele = file.extraireProchainPassage(false).orElseThrow();

        assertThat(appele.id()).isEqualTo(groupe);
        assertThat(appele.tourConsomme()).isEqualTo(2L);
        assertThat(file.etat().tourOuvert()).isEqualTo(1);
        assertThat(file.etat().derniersToursConsommes()).containsExactlyInAnyOrderEntriesOf(Map.of(ANA, 2L, BORIS, 1L));
        assertThat(file.ordreEffectif()).extracting(Passage::id).containsExactly(celia, boris, ana);
        assertThat(file.ordreEffectif()).extracting(Passage::tourNormal).containsExactly(1L, 2L, 3L);
    }

    @Test
    void unAppelNormalConsommeLeTourCommunPourTousLesInterpretes() {
        inscrire("Boris", BORIS);
        UUID groupe = inscrire("Groupe", ANA, BORIS, CELIA);
        file.extraireProchainPassage(false);

        assertThat(file.extraireProchainPassage(false).orElseThrow().id()).isEqualTo(groupe);
        assertThat(file.etat().derniersToursConsommes()).containsExactlyInAnyOrderEntriesOf(Map.of(ANA, 2L, BORIS, 2L, CELIA, 2L));
        assertThat(file.etat().tourOuvert()).isEqualTo(2);
        assertThat(file.trouver(inscrire("Célia", CELIA)).tourNormal()).isEqualTo(3);
    }

    @Test
    void appliqueLaLimiteEtLeDoublonAMembresEtInvitesSansMutation() {
        file.reglerLimite(1);
        UUID duo = inscrire("Chanson", ANA);
        inscrire("Chanson", BORIS);
        refuserSansMutation(() -> file.ajouterInterprete(cible(duo), BORIS), "PASSAGE_DEJA_INSCRIT");
        refuserSansMutation(() -> file.inscrire(nouveau("Autre chanson", CELIA, BORIS)), "LIMITE_ATTEINTE");
        inscrire("Célia", CELIA);
        refuserSansMutation(() -> file.ajouterInterprete(cible(duo), CELIA), "LIMITE_ATTEINTE");

        assertThat(file.ajouterInterprete(cible(duo), DINA)).isInstanceOf(ResultatFile.Modifiee.class);
        refuserSansMutation(() -> file.inscrire(nouveau("Dina", DINA)), "LIMITE_ATTEINTE");
    }

    @Test
    void retirerUnInterpreteAvanceLeGroupeEtLibereLaReservationDeLInterprete() {
        UUID premier = inscrire("Boris 1", BORIS);
        UUID groupe = inscrire("Groupe", ANA, BORIS, CELIA);
        UUID second = inscrire("Boris 2", BORIS);
        UUID solo = inscrire("Ana", ANA);
        var ancienneCible = cible(groupe);

        assertThat(file.retirerInterprete(ancienneCible, BORIS, RECU)).isInstanceOf(ResultatFile.Modifiee.class);

        assertThat(file.ordreEffectif()).extracting(Passage::id).containsExactly(premier, groupe, second, solo);
        assertThat(file.ordreEffectif()).extracting(Passage::tourNormal).containsExactly(1L, 1L, 2L, 2L);
        assertThat(file.trouver(groupe).interpretes()).containsExactly(ANA, CELIA);
        assertThat(file.trouver(groupe).numeroModification()).isEqualTo(1);
        assertThat(file.trouver(groupe).inscripteurId()).isEqualTo(ANA);
        refuserSansMutation(() -> file.retirerInterprete(ancienneCible, CELIA, RECU), "COMMANDE_PERIMEE");
        file.extraireProchainPassage(false);
        file.extraireProchainPassage(false);
        refuserSansMutation(() -> file.retirerInterprete(cible(groupe), CELIA, RECU), "COMMANDE_PERIMEE");
    }

    @Test
    void leDepartDeLInscripteurTransmetSesGroupesEtRetireSonSoloPrioritaire() {
        UUID groupe = inscrire("Groupe", ANA, CELIA, BORIS);
        UUID solo = inscrire("Solo", ANA);
        UUID duo = inscrire("Duo", ANA, DINA);
        file.definirPriorite(cible(solo), true);

        assertThat(file.retirerJoueurParti(ANA, RECU)).isInstanceOf(ResultatFile.Modifiee.class);

        assertThat(file.trouver(groupe).inscripteurId()).isEqualTo(CELIA);
        assertThat(file.trouver(groupe).interpretes()).containsExactly(CELIA, BORIS);
        assertThat(file.trouver(groupe).numeroModification()).isEqualTo(1);
        assertThat(file.trouver(duo).inscripteurId()).isEqualTo(DINA);
        assertThat(file.trouver(duo).numeroModification()).isEqualTo(1);
        assertThat(file.ordreEffectif()).extracting(Passage::id).containsExactly(groupe, duo);
        assertThat(file.ordreEffectif()).extracting(Passage::tourNormal).containsExactly(1L, 1L);
        assertThat(file.trouver(solo).etat()).isEqualTo(EtatPassage.RETIRE);
        assertThat(file.trouver(solo).numeroModification()).isEqualTo(2);
        assertThat(file.etat().passagePrioritaireId()).isNull();

        file.extraireProchainPassage(false);
        var avant = file.etat();
        assertThat(file.retirerJoueurParti(CELIA, RECU)).isInstanceOf(ResultatFile.SansEffet.class);
        assertThat(file.etat()).isEqualTo(avant);
    }

    private CiblePassage cible(UUID id) {
        return new CiblePassage(id, file.trouver(id).numeroModification());
    }

    private void refuserSansMutation(Supplier<ResultatFile> commande, String code) {
        var avant = file.etat();
        assertThat(commande.get()).isInstanceOfSatisfying(ResultatFile.Refus.class,
                refus -> assertThat(refus.refus().code()).isEqualTo(code));
        assertThat(file.etat()).isEqualTo(avant);
    }

    private UUID inscrire(String contenu, UUID... interpretes) {
        var nouveau = nouveau(contenu, interpretes);
        assertThat(file.inscrire(nouveau)).isInstanceOf(ResultatFile.Modifiee.class);
        return nouveau.id();
    }

    private NouveauPassage nouveau(String contenu, UUID... interpretes) {
        return new NouveauPassage(UUID.randomUUID(), SOIREE, MANCHE, interpretes[0], List.of(interpretes),
                contenu, Duration.ofMinutes(3), ++numero, RECU);
    }
}
