package fr.croustyquizz.karaoke;

import static org.assertj.core.api.Assertions.*;

import fr.croustyquizz.manche.*;
import fr.croustyquizz.passage.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class FabriqueKaraokeTest {
	private final HorlogeFactice horloge = new HorlogeFactice();
	private final UUID soiree = UUID.randomUUID();
	private final UUID manche = UUID.randomUUID();
	private final UUID joueur = UUID.randomUUID();
	private int fins;
	private final ContexteDeManche contexte = new ContexteDeManche(soiree, manche,
			new ReglagesDeManche(1, Duration.ofSeconds(30)), horloge, () -> fins++);
	private final FabriqueKaraoke fabrique = new FabriqueKaraoke(Duration.ofSeconds(30));
	private final ReglagesKaraoke reglages = new ReglagesKaraoke(1);

	@Test
	void declareLeModeEtLesReglagesCommunsSansPlafondDePassages() {
		assertThat(fabrique.mode()).isEqualTo(ModeDeJeu.KARAOKE);
		assertThat(fabrique.typeReglages()).isEqualTo(ReglagesKaraoke.class);
		assertThat(fabrique.reglagesCommuns(new ReglagesKaraoke(EtatFileAttente.LIMITE_PASSAGES_MAX)))
				.isEqualTo(new ReglagesDeManche(1, Duration.ofSeconds(30)));
		var deroulement = fabrique.creer(contexte, reglages);
		assertThat(deroulement.instantane().etatManche()).isEqualTo(EtatManche.PRETE);
		assertThat(deroulement.instantane().file()).isEqualTo(new EtatFileAttente(soiree, manche,
				1, 1, 1, 1, null, List.of(), Map.of()));
		deroulement.demarrer();
		assertThatThrownBy(deroulement::demarrer).isInstanceOf(IllegalStateException.class);
	}

	private ContenuKaraoke contenu() {
		return new ContenuKaraoke(new Chanson("opaque", 42, "Titre", "Artiste", null, 180000L,
				EtatParoles.SYNCHRONISEES), List.of(new LigneParoles(0, "Paroles")));
	}

	private EtatKaraokePersiste etatPersiste() {
		var file = FileAttente.depuis(new EtatFileAttente(soiree, manche, 3, 1, 1, 1, null, List.of(), Map.of()));
		UUID premier = UUID.randomUUID(); UUID second = UUID.randomUUID();
		file.inscrire(new NouveauPassage(premier, soiree, manche, joueur, List.of(joueur), "opaque",
				Duration.ofMinutes(3), 1, horloge.maintenant()));
		file.extraireProchainPassage(false);
		file.inscrire(new NouveauPassage(second, soiree, manche, joueur, List.of(joueur), "opaque",
				Duration.ofMinutes(3), 2, horloge.maintenant()));
		file.definirPriorite(new CiblePassage(second, 0), true);
		var capture = file.etat(); var passages = new ArrayList<>(capture.passages());
		var appele = passages.getFirst();
		passages.set(0, new Passage(appele.id(), soiree, manche, joueur, appele.interpretes(), "opaque",
				appele.duree(), 1, 2, EtatPassage.TERMINE, appele.inscriptionLe(), 1, 1, 1, 1L, null));
		return new EtatKaraokePersiste(new EtatFileAttente(soiree, manche, 3, capture.tourOuvert(),
				capture.prochainOrdreInscription(), capture.prochaineCleOrdre(), second, passages,
				capture.derniersToursConsommes()), Map.of(premier, contenu(), second, contenu()));
	}

	@Test
	void restaureExactementChaqueEtatSansRelanceNiReglageInitial() {
		var etat = etatPersiste();
		for (EtatManche cycle : List.of(EtatManche.EN_COURS, EtatManche.EN_PAUSE, EtatManche.TERMINEE)) {
			var restaure = fabrique.restaurer(contexte, reglages, etat, cycle);
			assertThat(restaure.instantane()).isEqualTo(new InstantaneKaraoke(cycle, etat.file(), etat.contenus()));
			assertThat(restaure.estTerminee()).isEqualTo(cycle == EtatManche.TERMINEE);
			assertThat(restaure.instantane().file().limitePassagesEnAttente()).isEqualTo(3);
			assertThatThrownBy(restaure::demarrer).isInstanceOf(IllegalStateException.class);
			horloge.avancer(Duration.ofHours(1));
			assertThat(restaure.instantane()).isEqualTo(new InstantaneKaraoke(cycle, etat.file(), etat.contenus()));
		}
		assertThat(fins).isZero(); assertThat(horloge.minuteursEnAttente()).isZero();
		assertThatThrownBy(() -> fabrique.restaurer(contexte, reglages, etat, EtatManche.PRETE))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void reconstruitLesMotifsLaPrioriteEtLesConsommationsSansAttente() {
		var etat = etatPersiste(); var restaure = fabrique.restaurer(contexte, reglages, etat, EtatManche.EN_COURS);
		var file = FileAttente.depuis(restaure.instantane().file());
		assertThat(DeroulementKaraoke.motifInscription(file, joueur, contenu().chanson()))
				.contains(MotifIndisponibilite.PASSAGE_DEJA_INSCRIT);
		file.extraireProchainPassage(false);
		assertThat(file.etat().derniersToursConsommes()).containsEntry(joueur, 2L);
		var sansAttente = new EtatKaraokePersiste(file.etat(), etat.contenus());
		var secondeRestauration = fabrique.restaurer(contexte, reglages, sansAttente, EtatManche.EN_PAUSE);
		var reconstruite = FileAttente.depuis(secondeRestauration.instantane().file());
		assertThat(DeroulementKaraoke.motifInscription(reconstruite, joueur, contenu().chanson())).isEmpty();
		reconstruite.inscrire(new NouveauPassage(UUID.randomUUID(), soiree, manche, joueur, List.of(joueur),
				"autre", Duration.ofMinutes(3), 3, horloge.maintenant()));
		assertThat(reconstruite.ordreEffectif().getFirst().tourNormal()).isEqualTo(3);
		assertThat(secondeRestauration.instantane().contenus()).hasSize(2);
	}

	@Test
	void copieLesCollectionsEnProfondeurEtIsoleLesRestaurations() {
		var lignes = new ArrayList<>(List.of(new LigneParoles(0, "Paroles")));
		var contenu = new ContenuKaraoke(contenu().chanson(), lignes); lignes.clear();
		assertThat(contenu.lignes()).containsExactly(new LigneParoles(0, "Paroles"));
		var etat = etatPersiste(); var passages = new ArrayList<>(etat.file().passages());
		var consommations = new HashMap<>(etat.file().derniersToursConsommes());
		var contenus = new HashMap<>(etat.contenus());
		var interpretes = new ArrayList<>(List.of(joueur)); var p = passages.getFirst();
		var passage = new Passage(p.id(), soiree, manche, joueur, interpretes, "opaque", p.duree(), 1, 2,
				EtatPassage.TERMINE, p.inscriptionLe(), 1, 1, 1, 1L, null);
		interpretes.clear(); assertThat(passage.interpretes()).containsExactly(joueur);
		var file = new EtatFileAttente(soiree, manche, 3, 1, 3, 3, etat.file().passagePrioritaireId(), passages, consommations);
		var persiste = new EtatKaraokePersiste(file, contenus);
		var instantane = new InstantaneKaraoke(EtatManche.EN_COURS, file, contenus);
		passages.clear(); consommations.clear(); contenus.clear();
		assertThat(persiste).isEqualTo(etat); assertThat(instantane.contenus()).hasSize(2);
		var premier = fabrique.restaurer(contexte, reglages, persiste, EtatManche.EN_COURS);
		var second = fabrique.restaurer(contexte, reglages, persiste, EtatManche.EN_COURS);
		premier.terminer(new CommandePilotage(new Acteur(Acteur.Type.ANIMATEUR, joueur, soiree), UUID.randomUUID(), horloge.maintenant()));
		assertThat(second.instantane()).isEqualTo(instantane);
		assertThatThrownBy(() -> instantane.contenus().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> persiste.contenus().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> contenu.lignes().clear()).isInstanceOf(UnsupportedOperationException.class);
	}
    private static final UUID PASSAGE = new UUID(0, 31);
    private static final UUID ETAPE = new UUID(0, 32);
    private static final UUID LECTEUR = new UUID(0, 33);
    private static final UUID LECTURE = new UUID(0, 34);
    private static final UUID PAROLES = new UUID(0, 35);
    private static final UUID DEMANDE = new UUID(0, 36);

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "APPELE, EN_COURS, PREPAREE",
        "EN_COURS, EN_COURS, DEMARREE",
        "EN_COURS, EN_PAUSE, DEMARREE",
        "EN_COURS, EN_PAUSE, EN_ERREUR",
        "TERMINE, EN_COURS, DEMARREE"
    })
    void restaureLeCycleLesLecturesEtLeLecteurSansNouvelIdentifiant(
            EtatPassage etatPassage, EtatManche etatManche, EtatLecture etatLecture) {
        var depart = horloge.maintenant();
        var persiste = etatAvecCourant(etatPassage, etatManche, etatLecture);
        boolean enChant = etatPassage == EtatPassage.EN_COURS && etatManche == EtatManche.EN_COURS;
        horloge.avancer(Duration.ofSeconds(30));
        var fabriqueSansIdentifiant = new FabriqueKaraoke(Duration.ofSeconds(30),
                () -> { throw new AssertionError("La restauration ne crée pas d’identifiant."); });
        var restaure = fabriqueSansIdentifiant.restaurer(contexte, reglages, persiste, etatManche);
        assertThat(restaure.instantane()).isEqualTo(new InstantaneKaraoke(etatManche, persiste.file(),
                persiste.contenus(), persiste.cycle(), persiste.lectures(), LECTEUR));
        assertThat(restaure.causeDernierMinuteur()).isNull();
        assertThat(horloge.minuteursEnAttente()).isEqualTo(enChant ? 1 : 0);
        horloge.avancer(Duration.ofSeconds(79));
        assertThat(restaure.instantane().cycle()).isEqualTo(persiste.cycle());
        horloge.avancer(Duration.ofSeconds(1));
        if (enChant) {
            assertThat(restaure.instantane().cycle().clotureLe()).isEqualTo(depart.plusSeconds(110));
            assertThat(restaure.instantane().lectures().get(PASSAGE).demandeId()).isNull();
            assertThat(fabriqueSansIdentifiant.typeEvenementMinuteur(restaure)).isEqualTo("passage-termine");
        } else {
            assertThat(restaure.instantane().cycle()).isEqualTo(persiste.cycle());
        }
        assertThat(fins).isZero();
    }

    @Test
    void restaureUneFinDepasseeAuProchainRappelSansCloreALaLecture() {
        var etat = etatAvecCourant(EtatPassage.EN_COURS, EtatManche.EN_COURS, EtatLecture.DEMARREE);
        horloge.avancer(Duration.ofMinutes(3));
        var restaure = fabrique.restaurer(contexte, reglages, etat, EtatManche.EN_COURS);
        var avant = restaure.instantane();
        assertThat(CycleDePassage.projeter(avant.file(), avant.cycle(), false, horloge.maintenant())
                .positionChronologiqueMs()).isEqualTo(100_000L);
        assertThat(restaure.estimerAttentes()).isEmpty();
        assertThat(restaure.instantane()).isEqualTo(avant);
        assertThat(horloge.minuteursEnAttente()).isEqualTo(1);
        horloge.avancer(Duration.ZERO);
        assertThat(restaure.instantane().cycle().clotureLe()).isEqualTo(horloge.maintenant());
        assertThat(restaure.instantane().file().passages().getFirst().etat()).isEqualTo(EtatPassage.TERMINE);
    }

    @Test
    void figeLaCarteDeLecturesDansLesDeuxCaptures() {
        var etat = etatAvecCourant(EtatPassage.EN_COURS, EtatManche.EN_PAUSE, EtatLecture.EN_ERREUR);
        var lectures = new HashMap<>(etat.lectures());
        var persiste = new EtatKaraokePersiste(etat.file(), etat.contenus(), etat.cycle(), lectures, LECTEUR);
        var instantane = new InstantaneKaraoke(EtatManche.EN_PAUSE, etat.file(), etat.contenus(),
                etat.cycle(), lectures, LECTEUR);
        lectures.clear();
        assertThat(persiste.lectures()).containsExactlyEntriesOf(etat.lectures());
        assertThat(instantane.lectures()).containsExactlyEntriesOf(etat.lectures());
        assertThatThrownBy(() -> persiste.lectures().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> instantane.lectures().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void traiteUnCycleAbsentCommeUneCaptureHistorique() {
        var etat = etatPersiste();
        var instantane = new InstantaneKaraoke(EtatManche.EN_COURS, etat.file(), etat.contenus(), null, Map.of(), null);
        var persiste = new EtatKaraokePersiste(etat.file(), etat.contenus(), null, Map.of(), null);
        assertThat(instantane).isEqualTo(new InstantaneKaraoke(EtatManche.EN_COURS, etat.file(), etat.contenus()));
        assertThat(persiste).isEqualTo(etat);
        assertThat(fabrique.restaurer(contexte, reglages, persiste, EtatManche.EN_COURS).instantane()).isEqualTo(instantane);
        assertThat(horloge.minuteursEnAttente()).isZero();
    }

    private EtatKaraokePersiste etatAvecCourant(EtatPassage etatPassage, EtatManche etatManche, EtatLecture etatLecture) {
        var debut = horloge.maintenant();
        var contenu = contenu();
        var file = FileAttente.depuis(new EtatFileAttente(soiree, manche, 1, 1, 1, 1, null, List.of(), Map.of()));
        file.inscrire(new NouveauPassage(PASSAGE, soiree, manche, joueur, List.of(joueur), "opaque",
                Duration.ofMinutes(3), 1, debut));
        file.appelerPassage(new CiblePassage(PASSAGE, 0));
        boolean appele = etatPassage == EtatPassage.APPELE;
        boolean clos = etatPassage == EtatPassage.TERMINE;
        if (!appele) { file.demarrerPassage(new CiblePassage(PASSAGE, 1)); }
        var cloture = clos ? debut.plusSeconds(20) : null;
        if (clos) {
            file.clorePassage(new CiblePassage(PASSAGE, 2), EtatPassage.TERMINE,
                    new TraceTerminale("FIN_LECTURE", null, cloture));
        }
        var cycle = new EtatCyclePassage(PASSAGE, ETAPE, debut, debut.plusSeconds(30),
                appele ? null : debut.plusSeconds(10), appele ? null : Duration.ofSeconds(100), Duration.ZERO,
                etatManche == EtatManche.EN_PAUSE ? debut.plusSeconds(20) : null, cloture);
        boolean demandeEnCours = !clos && etatLecture == EtatLecture.DEMARREE;
        var lecture = new LecturePassage(LECTURE, PASSAGE, LECTEUR, etatLecture,
                SourceLecture.FICHIER_LOCAL, new FichierAudioLocal("titre.mp3", 10, 100_000L),
                null, OrigineParoles.VERSION_ADAPTEE, new ParolesDuPassage(PAROLES, 90_000, contenu.lignes()), 43,
                100, 2, demandeEnCours ? DEMANDE : null, demandeEnCours ? debut.plusSeconds(12) : null);
        return new EtatKaraokePersiste(file.etat(), Map.of(PASSAGE, contenu), cycle, Map.of(PASSAGE, lecture), LECTEUR);
    }
}
