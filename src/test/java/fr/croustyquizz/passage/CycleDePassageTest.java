package fr.croustyquizz.passage;

import static org.assertj.core.api.Assertions.*;

import fr.croustyquizz.manche.Horloge;
import fr.croustyquizz.manche.HorlogeFactice;
import fr.croustyquizz.manche.Minuteur;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class CycleDePassageTest {
    private static final UUID SOIREE = new UUID(0, 1);
    private static final UUID MANCHE = new UUID(0, 2);
    private static final UUID JOUEUR = new UUID(0, 3);
    private static final UUID PASSAGE = new UUID(0, 4);
    private static final UUID ETAPE = new UUID(0, 5);
    private static final Duration APPEL = Duration.ofSeconds(30);
    private final HorlogeFactice horloge = new HorlogeFactice();
    private final Instant debut = horloge.maintenant();
    private final FileAttente file = FileAttente.depuis(
            new EtatFileAttente(SOIREE, MANCHE, 2, 1, 1, 1, null, List.of(), Map.of()));
    private final List<CloturePassage> clotures = new ArrayList<>();

    private CycleDePassage creer(Horloge temps) {
        file.inscrire(new NouveauPassage(PASSAGE, SOIREE, MANCHE, JOUEUR, List.of(JOUEUR),
                "chanson", Duration.ofSeconds(100), 1, debut));
        return new CycleDePassage(file, temps, APPEL, EtatCyclePassage.vide(), clotures::add);
    }

    @Test
    void valideLaCoherenceDesDatesDuCycle() {
        assertThat(EtatCyclePassage.vide()).isEqualTo(
                new EtatCyclePassage(null, null, null, null, null, null, Duration.ZERO, null, null));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new EtatCyclePassage(PASSAGE, ETAPE, debut, null, null, null, Duration.ZERO, null, null));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new EtatCyclePassage(PASSAGE, ETAPE, debut, debut, debut, null, Duration.ZERO, null, null));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new EtatCyclePassage(PASSAGE, ETAPE, debut, debut, null, null, Duration.ZERO, debut, debut));
    }

    @Test
    void refuseLesDureesIncompatiblesAvecUnPassageEnCours() {
        for (Duration reference : List.of(Duration.ZERO, Duration.ofMillis(-1))) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new EtatCyclePassage(PASSAGE, ETAPE, debut, debut, debut, reference, Duration.ZERO, null, null));
        }
        assertThatIllegalArgumentException().isThrownBy(() ->
                new EtatCyclePassage(PASSAGE, ETAPE, debut, debut, debut, APPEL, Duration.ofMillis(-1), null, null));
        assertThat(new EtatCyclePassage(PASSAGE, ETAPE, debut, debut, debut, APPEL, Duration.ZERO, null, null)
                .dureeReference()).isEqualTo(APPEL);
    }

    @Test
    void appelleUneSeuleFoisSansMinuterLEcheance() {
        var cycle = creer(horloge);
        assertThat(projection(cycle, false)).isEqualTo(
                new ProjectionCycle(null, false, null, null, null, null, null, null, null, null, 0, null, null, debut));
        assertThat(cycle.appeler(cible(), ETAPE, debut)).isInstanceOf(ResultatFile.Modifiee.class);
        assertThat(projection(cycle, false)).isEqualTo(new ProjectionCycle(file.trouver(PASSAGE), true, "APPELE",
                ETAPE, debut, debut.plusSeconds(30), 30_000L, null, null, null, 0, null, debut.plusSeconds(130), debut));
        var avant = cycle.etat();
        var fileAvant = file.etat();
        assertThat(cycle.appeler(cible(), new UUID(0, 6), debut)).isInstanceOfSatisfying(
                ResultatFile.Refus.class, r -> assertThat(r.refus().code()).isEqualTo("ETAT_INCOMPATIBLE"));
        horloge.avancer(Duration.ofSeconds(60));
        assertThat(projection(cycle, false).delaiAppelRestantMs()).isZero();
        assertThat(projection(cycle, false).prochainAppelPrevuLe()).isNull();
        assertThat(cycle.etat()).isEqualTo(avant);
        assertThat(file.etat()).isEqualTo(fileAvant);
        assertThat(horloge.minuteursEnAttente()).isZero();
    }

    @Test
    void appelleLePremierDeLOrdreEffectifSeulementSiLaFileEstLibre() {
        var cycle = new CycleDePassage(file, horloge, APPEL, EtatCyclePassage.vide(), clotures::add);
        var etapes = new ArrayDeque<>(List.of(ETAPE, new UUID(0, 6)));
        var vide = file.etat();
        assertThat(cycle.appelerSuivantSiLibre(etapes::remove, debut)).isFalse();
        assertThat(file.etat()).isEqualTo(vide);
        assertThat(cycle.etat()).isEqualTo(EtatCyclePassage.vide());

        UUID prioritaire = new UUID(0, 7);
        file.inscrire(new NouveauPassage(PASSAGE, SOIREE, MANCHE, JOUEUR, List.of(JOUEUR),
                "première", APPEL, 1, debut));
        file.inscrire(new NouveauPassage(prioritaire, SOIREE, MANCHE, JOUEUR, List.of(JOUEUR),
                "prioritaire", APPEL, 2, debut));
        file.definirPriorite(new CiblePassage(prioritaire, 0), true);

        assertThat(cycle.appelerSuivantSiLibre(etapes::remove, debut)).isTrue();
        assertThat(cycle.courantActif().id()).isEqualTo(prioritaire);
        assertThat(cycle.etat().etapeId()).isEqualTo(ETAPE);
        var fileAvecCourant = file.etat();
        var courant = cycle.etat();
        assertThat(cycle.appelerSuivantSiLibre(etapes::remove, debut)).isFalse();
        assertThat(file.etat()).isEqualTo(fileAvecCourant);
        assertThat(cycle.etat()).isEqualTo(courant);
        assertThat(etapes).containsExactly(new UUID(0, 6));

        cycle.clore(EtatPassage.SAUTE, TraceTerminale.PASSE_PAR_ANIMATEUR, JOUEUR, debut);
        assertThat(cycle.appelerSuivantSiLibre(etapes::remove, debut)).isTrue();
        assertThat(cycle.courantActif().id()).isEqualTo(PASSAGE);
        assertThat(etapes).isEmpty();
    }

    @Test
    void uneFileHistoriqueSansChronologieNeRetrouvePasDeCourant() {
        var cycle = creer(horloge);
        file.extraireProchainPassage(false);
        assertThat(cycle.courantActif()).isNull();
        assertThat(projection(cycle, false).courant()).isNull();
        assertThat(horloge.minuteursEnAttente()).isZero();
    }

    @Test
    void gardeLaFileIntacteSiLIdentifiantDEtapeNePeutEtreCree() {
        var cycle = creer(horloge);
        var avant = file.etat();
        assertThatIllegalStateException().isThrownBy(() -> cycle.appeler(cible(), debut,
                () -> { throw new IllegalStateException("Identifiant indisponible."); }));
        assertThat(file.etat()).isEqualTo(avant);
        assertThat(cycle.etat()).isEqualTo(EtatCyclePassage.vide());
        assertThat(horloge.minuteursEnAttente()).isZero();
    }

    @Test
    void figeDeuxPausesDAppelSansLesCompterDansLaDureeChantee() {
        var cycle = creer(horloge);
        cycle.appeler(cible(), ETAPE, debut);
        avancer(5);
        cycle.suspendre(horloge.maintenant());
        avancer(20);
        cycle.suspendre(horloge.maintenant());
        assertThat(projection(cycle, true).delaiAppelRestantMs()).isEqualTo(25_000L);
        assertThat(projection(cycle, true).echeanceAppelLe()).isNull();
        assertThat(projection(cycle, true).prochainAppelPrevuLe()).isNull();
        cycle.reprendre(horloge.maintenant());
        avancer(7);
        cycle.suspendre(horloge.maintenant());
        avancer(10);
        cycle.reprendre(horloge.maintenant());
        cycle.reprendre(horloge.maintenant());
        assertThat(projection(cycle, false).delaiAppelRestantMs()).isEqualTo(18_000L);
        assertThat(projection(cycle, false).echeanceAppelLe()).isEqualTo(debut.plusSeconds(60));
        cycle.demarrer(cible(), ETAPE, Duration.ofSeconds(120), horloge.maintenant());
        assertThat(projection(cycle, false).tempsPauseMs()).isZero();
        assertThat(projection(cycle, false).finPrevueLe()).isEqualTo(debut.plusSeconds(162));
    }

    @Test
    void unePauseApresLEcheanceGarantitZeroEtLeDepartTardifRestePossible() {
        var cycle = creer(horloge);
        cycle.appeler(cible(), ETAPE, debut);
        avancer(31);
        cycle.suspendre(horloge.maintenant());
        avancer(10);
        assertThat(projection(cycle, true).delaiAppelRestantMs()).isZero();
        cycle.reprendre(horloge.maintenant());
        assertThat(projection(cycle, false).delaiAppelRestantMs()).isZero();
        assertThat(cycle.demarrer(cible(), ETAPE, Duration.ofSeconds(10), horloge.maintenant()))
                .isInstanceOf(ResultatFile.Modifiee.class);
    }

    @Test
    void refuseUnDepartPerimeSansToucherAuCycleNiAuMinuteur() {
        var cycle = creer(horloge);
        cycle.appeler(cible(), ETAPE, debut);
        var avant = cycle.etat();
        var fileAvant = file.etat();
        for (var resultat : List.of(
                cycle.demarrer(cible(), new UUID(0, 6), APPEL, debut),
                cycle.demarrer(new CiblePassage(new UUID(0, 7), 1), ETAPE, APPEL, debut),
                cycle.demarrer(new CiblePassage(PASSAGE, 0), ETAPE, APPEL, debut))) {
            assertThat(resultat).isInstanceOfSatisfying(ResultatFile.Refus.class,
                    r -> assertThat(r.refus().code()).isEqualTo("COMMANDE_PERIMEE"));
        }
        assertThat(cycle.etat()).isEqualTo(avant);
        assertThat(file.etat()).isEqualTo(fileAvant);
        assertThat(horloge.minuteursEnAttente()).isZero();
    }

    @Test
    void figeLaPositionEnPauseEtAttendLeResteDepuisLaReprise() {
        var cycle = creer(horloge);
        cycle.appeler(cible(), ETAPE, debut);
        avancer(12);
        cycle.demarrer(cible(), ETAPE, Duration.ofSeconds(100), debut.plusSeconds(10));
        assertThat(projection(cycle, false)).isEqualTo(new ProjectionCycle(file.trouver(PASSAGE), true, "EN_COURS",
                ETAPE, debut, null, null, debut.plusSeconds(10), debut.plusSeconds(110), 2_000L, 0, null,
                debut.plusSeconds(110), horloge.maintenant()));
        avancer(8);
        cycle.suspendre(horloge.maintenant());
        avancer(200);
        assertThat(projection(cycle, true)).isEqualTo(new ProjectionCycle(file.trouver(PASSAGE), true, "EN_PAUSE",
                ETAPE, debut, null, null, debut.plusSeconds(10), null, 10_000L, 0, null, null, horloge.maintenant()));
        assertThat(clotures).isEmpty();
        cycle.reprendre(horloge.maintenant());
        assertThat(projection(cycle, false).tempsPauseMs()).isEqualTo(200_000L);
        avancer(89);
        assertThat(clotures).isEmpty();
        avancer(1);
        assertThat(clotures).containsExactly(new CloturePassage(PASSAGE, ETAPE, EtatPassage.TERMINE,
                "FIN_PREVUE", debut.plusSeconds(310)));
        assertThat(projection(cycle, false)).isEqualTo(new ProjectionCycle(file.trouver(PASSAGE), false, "TERMINE",
                ETAPE, debut, null, null, debut.plusSeconds(10), null, null, 200_000L, debut.plusSeconds(310), null,
                horloge.maintenant()));
    }

    @Test
    void ignoreLesRappelsAnnulesOuRemplacesMemePourLaMemeEtape() {
        Horloge sansAnnulation = new Horloge() {
            @Override public Instant maintenant() { return horloge.maintenant(); }
            @Override public Minuteur programmer(Duration delai, Runnable rappel) {
                horloge.programmer(delai, rappel);
                return () -> { };
            }
        };
        var cycle = creer(sansAnnulation);
        cycle.appeler(cible(), ETAPE, debut);
        cycle.demarrer(cible(), ETAPE, Duration.ofSeconds(10), debut);
        avancer(2);
        cycle.suspendre(horloge.maintenant());
        avancer(3);
        cycle.reprendre(horloge.maintenant());
        avancer(5);
        assertThat(clotures).isEmpty();
        avancer(3);
        assertThat(clotures).containsExactly(new CloturePassage(PASSAGE, ETAPE, EtatPassage.TERMINE,
                "FIN_PREVUE", debut.plusSeconds(13)));
    }

    @Test
    void clotureEnPauseSansCompterLaPauseEnCoursEtOublieLeClosAuProchainAppel() {
        var cycle = creer(horloge);
        cycle.appeler(cible(), ETAPE, debut);
        cycle.suspendre(debut);
        avancer(10);
        assertThat(cycle.clore(EtatPassage.SAUTE, "PASSE_PAR_ANIMATEUR", JOUEUR, horloge.maintenant()))
                .isInstanceOf(ResultatFile.Modifiee.class);
        assertThat(projection(cycle, true)).isEqualTo(new ProjectionCycle(file.trouver(PASSAGE), false, "SAUTE",
                ETAPE, debut, null, null, null, null, null, 0, horloge.maintenant(), null, horloge.maintenant()));
        assertThat(cycle.etat().pauseDepuis()).isNull();
        UUID suivant = new UUID(0, 9);
        file.inscrire(new NouveauPassage(suivant, SOIREE, MANCHE, JOUEUR, List.of(JOUEUR),
                "autre", Duration.ofSeconds(100), 2, horloge.maintenant()));
        cycle.appeler(new CiblePassage(suivant, 0), new UUID(0, 10), horloge.maintenant());
        assertThat(cycle.etat().clotureLe()).isNull();
        assertThat(projection(cycle, false).courant().id()).isEqualTo(suivant);
        cycle.clore(EtatPassage.ANNULE, "MANCHE_TERMINEE", JOUEUR, horloge.maintenant());
        assertThat(projection(cycle, false).courant()).isNull();
        assertThat(clotures).hasSize(2);
        assertThat(cycle.clore(EtatPassage.SAUTE, "PASSE_PAR_ANIMATEUR", JOUEUR, horloge.maintenant()))
                .isInstanceOf(ResultatFile.Refus.class);
    }

    private ProjectionCycle projection(CycleDePassage cycle, boolean enPause) {
        return CycleDePassage.projeter(file.etat(), cycle.etat(), enPause, horloge.maintenant());
    }

    private CiblePassage cible() { return new CiblePassage(PASSAGE, file.trouver(PASSAGE).numeroModification()); }
    private void avancer(long secondes) { horloge.avancer(Duration.ofSeconds(secondes)); }
}
