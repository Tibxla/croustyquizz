package fr.croustyquizz.passage;

import static org.assertj.core.api.Assertions.assertThat;

import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.HorlogeFactice;
import fr.croustyquizz.manche.Minuteur;
import fr.croustyquizz.manche.Refuse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class VoteDuPublicTest {
    private static final UUID SOIREE = new UUID(0, 1);
    private static final UUID MANCHE = new UUID(0, 2);
    private static final UUID INTERPRETE = new UUID(0, 3);
    private static final UUID VOTANT = new UUID(0, 4);
    private static final UUID AUTRE_VOTANT = new UUID(0, 5);
    private static final UUID PREMIER = new UUID(0, 6);
    private static final UUID SECOND = new UUID(0, 7);
    private static final Duration DUREE = Duration.ofSeconds(30);

    private final HorlogeFactice horloge = new HorlogeFactice();
    private final Instant debut = horloge.maintenant();
    private final FileAttente file = FileAttente.depuis(
            new EtatFileAttente(SOIREE, MANCHE, 2, 1, 1, 1, null, List.of(), Map.of()));
    private final List<ClotureVote> clotures = new ArrayList<>();

    private VoteDuPublic creer() {
        inscrire(PREMIER, 1);
        inscrire(SECOND, 2);
        return new VoteDuPublic(file, horloge, DUREE, EtatVoteDuPublic.vide(), clotures::add);
    }

    private void inscrire(UUID passage, int numero) {
        file.inscrire(new NouveauPassage(passage, SOIREE, MANCHE, INTERPRETE, List.of(INTERPRETE),
                "chanson-" + numero, DUREE, numero, debut));
    }

    @Test
    void unVoteOuvertPendantLaPauseRecoitTrenteSecondesALaReprise() {
        var vote = creer();
        vote.ouvrir(PREMIER, debut, true);
        horloge.avancer(Duration.ofSeconds(45));
        assertThat(VoteDuPublic.projeter(vote.etat().votes().getFirst(), horloge.maintenant()))
                .isEqualTo(new ProjectionVote(PREMIER, true, 30_000, debut.plusSeconds(45), null, 0, 0, null, false));
        vote.reprendre(horloge.maintenant());
        horloge.avancer(Duration.ofSeconds(29));
        assertThat(VoteDuPublic.projeter(vote.etat().votes().getFirst(), horloge.maintenant()).delaiRestantMs()).isEqualTo(1000);
        horloge.avancer(Duration.ofSeconds(1));
        assertThat(VoteDuPublic.projeter(vote.etat().votes().getFirst(), horloge.maintenant()))
                .isEqualTo(new ProjectionVote(PREMIER, false, 0, debut.plusSeconds(75), debut.plusSeconds(75), 0, 0, null, false));
        assertThat(clotures).containsExactly(new ClotureVote(PREMIER, 0, 0, debut.plusSeconds(75)));
    }

    @ParameterizedTest
    @CsvSource({"3, 13, 43", "2, 9, 45", "4, 17, 43", "1, 1, 10", "1, 5, 50", "0, 0, "})
    void arronditLaMoyenneAuDixieme(long nombreVotes, long sommeNotes, Integer attendu) {
        assertThat(VoteDuPublic.moyenneDixiemes(nombreVotes, sommeNotes)).isEqualTo(attendu);
    }

    @Test
    void laProjectionDonneLaMoyenneDesTroisNotesSeulementApresCloture() {
        var vote = creer();
        vote.ouvrir(PREMIER, debut, false);
        var cible = new CiblePassage(PREMIER, 0);
        assertThat(vote.voter(VOTANT, cible, 4, debut, false)).isEmpty();
        assertThat(vote.voter(AUTRE_VOTANT, cible, 4, debut, false)).isEmpty();
        assertThat(vote.voter(new UUID(0, 8), cible, 5, debut, false)).isEmpty();
        var projection = VoteDuPublic.projeter(vote.etat().votes().getFirst(), debut);
        assertThat(projection.nombreVotes()).isEqualTo(3);
        assertThat(projection.sommeNotes()).isEqualTo(13);
        assertThat(projection.moyenneDixiemes()).isNull();
        horloge.avancer(DUREE);
        assertThat(VoteDuPublic.projeter(vote.etat().votes().getFirst(), horloge.maintenant()).moyenneDixiemes())
                .isEqualTo(43);
    }

    @Test
    void unDemarrageMarqueLesAutresVotesOuvertsOuClosEtLeDrapeauRestePose() {
        var vote = creer();
        var ancien = new EtatVotePassage(PREMIER, debut, debut.plus(DUREE), null, null, List.of());
        assertThat(ancien.suivantDemarre()).isFalse();
        vote.ouvrir(PREMIER, debut, false);
        horloge.avancer(Duration.ofSeconds(5));
        vote.ouvrir(SECOND, horloge.maintenant(), false);
        horloge.avancer(Duration.ofSeconds(25));
        UUID courant = new UUID(0, 9);
        vote.ouvrir(courant, horloge.maintenant(), false);
        assertThat(vote.etat().votes()).extracting(EtatVotePassage::ouvert).containsExactly(false, true, true);

        vote.signalerDemarrage(courant);
        assertThat(vote.etat().votes()).extracting(EtatVotePassage::suivantDemarre).containsExactly(true, true, false);
        var apres = vote.etat();
        vote.signalerDemarrage(courant);
        assertThat(vote.etat()).isEqualTo(apres);
        var maintenant = horloge.maintenant();
        assertThat(vote.voter(VOTANT, new CiblePassage(SECOND, 0), 4, maintenant, false)).isEmpty();
        vote.suspendre(maintenant);
        vote.reprendre(maintenant);
        horloge.avancer(Duration.ofSeconds(5));
        assertThat(vote.etat().votes()).extracting(EtatVotePassage::ouvert).containsExactly(false, false, true);
        assertThat(vote.etat().votes()).extracting(v -> VoteDuPublic.projeter(v, horloge.maintenant()).suivantDemarre())
                .containsExactly(true, true, false);
    }

    @Test
    void deuxVotesGardentLeursEcheancesEtChacuneDesPausesNeCompteQuUneFois() {
        var vote = creer();
        vote.ouvrir(PREMIER, debut, false);
        horloge.avancer(Duration.ofSeconds(5));
        vote.ouvrir(SECOND, horloge.maintenant(), false);
        horloge.avancer(Duration.ofSeconds(5));
        for (int i = 0; i < 2; i++) {
            vote.suspendre(horloge.maintenant());
            horloge.avancer(Duration.ofSeconds(10));
            vote.reprendre(horloge.maintenant());
        }
        horloge.avancer(Duration.ofSeconds(20));
        assertThat(clotures).containsExactly(new ClotureVote(PREMIER, 0, 0, debut.plusSeconds(50)));
        assertThat(vote.aUnVoteOuvert()).isTrue();
        horloge.avancer(Duration.ofSeconds(5));
        assertThat(clotures).endsWith(new ClotureVote(SECOND, 0, 0, debut.plusSeconds(55)));
        assertThat(vote.aUnVoteOuvert()).isFalse();
    }

    @Test
    void refuseDansLOrdreSansChangerLEtatMemeALInstantDeLEcheance() {
        var vote = creer();
        var cible = new CiblePassage(PREMIER, 0);
        var perimee = new CiblePassage(PREMIER, 1);
        vote.ouvrir(PREMIER, debut, false);
        assertThat(vote.voter(VOTANT, cible, 4, debut, false)).isEmpty();
        verifierRefus(vote, INTERPRETE, new CiblePassage(new UUID(0, 99), 0), debut, true, "RESSOURCE_INTROUVABLE");
        verifierRefus(vote, INTERPRETE, perimee, debut, true, "ETAT_INCOMPATIBLE");
        verifierRefus(vote, INTERPRETE, perimee, debut, false, "VOTE_INTERDIT");
        verifierRefus(vote, VOTANT, perimee, debut.plus(DUREE), false, "COMMANDE_PERIMEE");
        verifierRefus(vote, VOTANT, cible, debut.plus(DUREE), false, "VOTE_CLOS");
        verifierRefus(vote, VOTANT, cible, debut, false, "VOTE_DEJA_ENREGISTRE");
        verifierRefus(vote, AUTRE_VOTANT, new CiblePassage(SECOND, 0), debut, false, "VOTE_CLOS");
    }

    @ParameterizedTest
    @EnumSource(value = EtatPassage.class, names = {"EN_ATTENTE", "RETIRE", "ANNULE", "SAUTE", "TERMINE"})
    void refuseUnPassageSansVoteOuvertQuelQueSoitSonEtat(EtatPassage etat) {
        var vote = creer();
        var cible = new CiblePassage(PREMIER, 0);
        switch (etat) {
            case RETIRE -> file.retirer(INTERPRETE, cible, debut);
            case ANNULE -> file.annulerAttentes(debut, INTERPRETE);
            case SAUTE -> {
                file.appelerPassage(cible);
                file.clorePassage(new CiblePassage(PREMIER, 1), etat,
                        new TraceTerminale(TraceTerminale.PASSE_PAR_ANIMATEUR, INTERPRETE, debut));
            }
            case TERMINE -> {
                file.appelerPassage(cible);
                file.demarrerPassage(new CiblePassage(PREMIER, 1));
                file.clorePassage(new CiblePassage(PREMIER, 2), etat, new TraceTerminale(TraceTerminale.FIN_PREVUE, null, debut));
                vote.ouvrir(PREMIER, debut, false);
                horloge.avancer(DUREE);
            }
            case EN_ATTENTE -> { }
            default -> throw new AssertionError(etat);
        }
        Passage passage = file.trouver(PREMIER);
        assertThat(passage.etat()).isEqualTo(etat);
        verifierRefus(vote, VOTANT, new CiblePassage(PREMIER, passage.numeroModification()),
                horloge.maintenant(), false, "VOTE_CLOS");
    }

    private static void verifierRefus(VoteDuPublic vote, UUID joueur, CiblePassage cible, Instant recueLe,
            boolean enPause, String code) {
        var avant = vote.etat();
        assertThat(vote.voter(joueur, cible, 5, recueLe, enPause)).get().extracting(Refuse::code).isEqualTo(code);
        assertThat(vote.etat()).isEqualTo(avant);
    }

    @Test
    void laFinClotTousLesVotesAvecLesNotesRecuesEtAnnuleLesRappels() {
        var vote = creer();
        vote.ouvrir(PREMIER, debut, false);
        vote.ouvrir(SECOND, debut, false);
        assertThat(vote.voter(VOTANT, new CiblePassage(PREMIER, 0), 4, debut, false)).isEmpty();
        vote.suspendre(debut);
        vote.cloreTout(debut.plusSeconds(2));
        horloge.avancer(DUREE);
        assertThat(clotures).containsExactly(new ClotureVote(PREMIER, 1, 4, debut.plusSeconds(2)),
                new ClotureVote(SECOND, 0, 0, debut.plusSeconds(2)));
        assertThat(vote.etat().votes()).allMatch(etat -> !etat.ouvert() && etat.pauseDepuis() == null);
    }

    @Test
    void ignoreUnRappelAnnuleMemeApresRepriseEtReprogrammeUneEcheanceDepassee() {
        inscrire(PREMIER, 1);
        var rappels = new ArrayList<Runnable>();
        Horloge temps = new Horloge() {
            @Override public Instant maintenant() { return horloge.maintenant(); }
            @Override public Minuteur programmer(Duration delai, Runnable rappel) {
                rappels.add(rappel);
                return () -> { };
            }
        };
        var vote = new VoteDuPublic(file, temps, DUREE, EtatVoteDuPublic.vide(), clotures::add);
        vote.ouvrir(PREMIER, debut, false);
        vote.suspendre(debut);
        vote.reprendre(debut);
        rappels.getFirst().run();
        assertThat(vote.aUnVoteOuvert()).isTrue();
        horloge.avancer(Duration.ofSeconds(40));
        var restaure = new VoteDuPublic(file, horloge, DUREE, vote.etat(), clotures::add);
        restaure.reprogrammer(horloge.maintenant());
        horloge.avancer(Duration.ZERO);
        assertThat(clotures).containsExactly(new ClotureVote(PREMIER, 0, 0, debut.plusSeconds(40)));
    }

    @Test
    void unPassageAPlusieursSeVoteEnDesignantUnSeulDeSesInterpretesSansNote() {
        UUID trio = new UUID(0, 9), deuxieme = new UUID(0, 10), troisieme = new UUID(0, 11);
        inscrire(PREMIER, 1);
        file.inscrire(new NouveauPassage(trio, SOIREE, MANCHE, VOTANT, List.of(VOTANT), "trio", DUREE, 2, debut),
                List.of(deuxieme, troisieme));
        file.repondreInvitation(deuxieme, new CiblePassage(trio, 0), true);
        file.repondreInvitation(troisieme, new CiblePassage(trio, 1), true);
        var vote = new VoteDuPublic(file, horloge, DUREE, EtatVoteDuPublic.vide(), clotures::add,
                passage -> passage.interpretes().size() > 1);
        vote.ouvrir(trio, debut, false);
        vote.ouvrir(PREMIER, debut, false);
        var cible = new CiblePassage(trio, 2);

        verifierRefusDeDesignation(vote, AUTRE_VOTANT, cible, AUTRE_VOTANT, "DONNEES_INVALIDES");
        verifierRefusDeDesignation(vote, deuxieme, cible, troisieme, "VOTE_INTERDIT");
        verifierRefusDeDesignation(vote, AUTRE_VOTANT, new CiblePassage(PREMIER, 0), INTERPRETE, "DONNEES_INVALIDES");
        var avant = vote.etat();
        assertThat(vote.voter(AUTRE_VOTANT, cible, 5, debut, false)).hasValueSatisfying(
                refus -> assertThat(refus.code()).isEqualTo("DONNEES_INVALIDES"));
        assertThat(vote.etat()).isEqualTo(avant);

        assertThat(vote.designer(AUTRE_VOTANT, cible, troisieme, debut, false)).isEmpty();
        assertThat(vote.designer(INTERPRETE, cible, troisieme, debut, false)).isEmpty();
        assertThat(vote.designer(new UUID(0, 12), cible, VOTANT, debut, false)).isEmpty();
        verifierRefusDeDesignation(vote, AUTRE_VOTANT, cible, VOTANT, "VOTE_DEJA_ENREGISTRE");

        var etat = vote.etat().votes().stream().filter(v -> v.passageId().equals(trio)).findFirst().orElseThrow();
        assertThat(VoteDuPublic.voix(etat, file.trouver(trio))).containsExactly(
                new VoteDuPublic.Voix(VOTANT, 1), new VoteDuPublic.Voix(deuxieme, 0), new VoteDuPublic.Voix(troisieme, 2));
        assertThat(VoteDuPublic.projeter(etat, debut)).satisfies(projection -> {
            assertThat(projection.nombreVotes()).isEqualTo(3);
            assertThat(projection.sommeNotes()).isZero();
        });
        horloge.avancer(DUREE);
        assertThat(clotures).contains(new ClotureVote(trio, 3, 0, debut.plus(DUREE)));
    }

    private static void verifierRefusDeDesignation(VoteDuPublic vote, UUID joueur, CiblePassage cible, UUID designe,
            String code) {
        var avant = vote.etat();
        assertThat(vote.designer(joueur, cible, designe, Instant.EPOCH.plusSeconds(1), false))
                .hasValueSatisfying(refus -> assertThat(refus.code()).isEqualTo(code));
        assertThat(vote.etat()).isEqualTo(avant);
    }
}
