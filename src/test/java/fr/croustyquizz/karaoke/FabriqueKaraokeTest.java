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
		assertThat(fabrique.reglagesCommuns(new ReglagesKaraoke(500)))
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
}
