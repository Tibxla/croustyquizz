package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import fr.croustyquizz.TestcontainersConfiguration;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.DepotDeManches;
import fr.croustyquizz.manche.DepotDeManchesJpa;
import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.ModeDeJeu;
import fr.croustyquizz.manche.RefusDeCommande;
import fr.croustyquizz.passage.ParticipantsDePassage;
import fr.croustyquizz.passage.PorteeDePassage;
import jakarta.persistence.EntityManager;

/**
 * Portée et participants des Passages lus en base (#111), sans repli de démonstration :
 * présence, Exclusion, Soirée et mode de la Manche, état de la Soirée.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, DepotDeManchesJpa.class, PorteeDePassageSoiree.class,
		ParticipantsDePassageSoiree.class})
class PortsDePassageSoireeTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-19T20:00:00Z");

	@Autowired private PorteeDePassage portee;
	@Autowired private ParticipantsDePassage participants;
	@Autowired private DepotDeSoirees soirees;
	@Autowired private DepotDeJoueurs joueurs;
	@Autowired private DepotDeManches manches;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private EntityManager entites;

	private UUID soiree;
	private UUID lea;
	private UUID karaoke;

	@BeforeEach
	void soireeEnCoursAvecLeaEtUneMancheDeKaraoke() {
		soiree = soirees.saveAndFlush(new Soiree("Karaoké du vendredi", Format.SOLO, Pilotage.MANUEL, MAINTENANT)).getId();
		passerA(soiree, "EN_COURS");
		lea = joueurs.saveAndFlush(new Joueur(soiree, "Léa", JetonDeReconnexion.generer(new java.security.SecureRandom()), MAINTENANT)).getId();
		karaoke = manche(soiree, ModeDeJeu.KARAOKE, EtatManche.EN_COURS);
	}

	@Test
	void unJoueurPresentEntreEtGardeSonPseudo() {
		portee.verifier(joueur(soiree, lea), soiree, karaoke);
		assertThat(participants.estPresent(soiree, lea)).isTrue();
		assertThat(participants.pseudos(soiree)).containsOnlyKeys(lea).containsEntry(lea, "Léa");
		assertThat(portee.estActive(soiree, karaoke)).isTrue();
	}

	@Test
	void unJoueurExcluNEntrePlusEtDevientJoueurParti() {
		jdbc.update("update joueur set exclu = true where id = ?", lea);
		entites.clear();
		assertThatThrownBy(() -> portee.verifier(joueur(soiree, lea), soiree, karaoke)).isInstanceOf(RefusDeCommande.class);
		assertThat(participants.estPresent(soiree, lea)).isFalse();
		assertThat(participants.pseudos(soiree)).doesNotContainKey(lea);
	}

	@Test
	void unJoueurDUneAutreSoireeNEstPasPresentIci() {
		UUID autre = soirees.saveAndFlush(new Soiree("Autre", Format.SOLO, Pilotage.MANUEL, MAINTENANT)).getId();
		UUID tom = joueurs.saveAndFlush(new Joueur(autre, "Tom", JetonDeReconnexion.generer(new java.security.SecureRandom()), MAINTENANT)).getId();
		assertThatThrownBy(() -> portee.verifier(joueur(soiree, tom), soiree, karaoke)).isInstanceOf(RefusDeCommande.class);
		assertThat(participants.estPresent(soiree, tom)).isFalse();
		assertThat(participants.pseudos(soiree)).doesNotContainKey(tom);
	}

	@Test
	void uneMancheDUnAutreModeOuDUneAutreSoireeEstIntrouvable() {
		UUID quiz = manche(soiree, ModeDeJeu.QUIZ, EtatManche.PRETE);
		UUID autre = soirees.saveAndFlush(new Soiree("Autre", Format.SOLO, Pilotage.MANUEL, MAINTENANT)).getId();
		UUID ailleurs = manche(autre, ModeDeJeu.KARAOKE, EtatManche.EN_COURS);
		Acteur animateur = new Acteur(Acteur.Type.ANIMATEUR, UUID.randomUUID(), soiree);
		assertThatThrownBy(() -> portee.verifier(animateur, soiree, quiz)).isInstanceOf(RefusDeCommande.class);
		assertThatThrownBy(() -> portee.verifier(animateur, soiree, ailleurs)).isInstanceOf(RefusDeCommande.class);
		assertThatThrownBy(() -> portee.verifier(animateur, soiree, UUID.randomUUID())).isInstanceOf(RefusDeCommande.class);
	}

	@Test
	void seuleUneSoireeEnCoursFaitTournerLaFile() {
		UUID preparee = soirees.saveAndFlush(new Soiree("Prête", Format.SOLO, Pilotage.MANUEL, MAINTENANT)).getId();
		assertThat(portee.estActive(preparee, manche(preparee, ModeDeJeu.KARAOKE, EtatManche.EN_COURS))).isFalse();
		for (String etat : new String[] {"OUVERTE", "TERMINEE"}) {
			passerA(soiree, etat);
			assertThat(portee.estActive(soiree, karaoke)).as(etat).isFalse();
		}
	}

	@Test
	void uneMancheNonDemarreeOuTermineeNEstPasActive() {
		assertThat(portee.estActive(soiree, manche(soiree, ModeDeJeu.KARAOKE, EtatManche.PRETE))).isFalse();
		assertThat(portee.estActive(soiree, manche(soiree, ModeDeJeu.KARAOKE, EtatManche.TERMINEE))).isFalse();
		// Une seule Manche active par Soirée : la Manche en cours passe en pause.
		jdbc.update("update manche_deroulement set etat = 'EN_PAUSE' where manche_id = ?", karaoke);
		entites.clear();
		assertThat(portee.estActive(soiree, karaoke)).isTrue();
	}

	@Test
	void uneSoireeAbsenteSansRepliEstIntrouvable() {
		UUID inconnue = UUID.randomUUID();
		assertThatThrownBy(() -> portee.verifier(joueur(inconnue, UUID.randomUUID()), inconnue, karaoke))
				.isInstanceOf(RefusDeCommande.class);
		assertThat(portee.estActive(inconnue, karaoke)).isFalse();
		assertThat(participants.estPresent(inconnue, lea)).isFalse();
		assertThat(participants.pseudos(inconnue)).isEmpty();
	}

	private UUID manche(UUID soireeId, ModeDeJeu mode, EtatManche etat) {
		ManchePersistante manche = new ManchePersistante(UUID.randomUUID(), soireeId, mode, "{}", MAINTENANT);
		manche.changerEtat(etat, MAINTENANT);
		manches.enregistrer(manche);
		entites.flush();
		return manche.getMancheId();
	}

	private void passerA(UUID id, String etat) {
		jdbc.update("update soiree set etat = ?, code_acces = case when ? = 'PREPARATION' then null else coalesce(code_acces, 'K7PQ2M') end"
				+ " where id = ?", etat, etat, id);
		entites.clear();
	}

	private static Acteur joueur(UUID soireeId, UUID joueurId) {
		return new Acteur(Acteur.Type.JOUEUR, joueurId, soireeId);
	}

}
