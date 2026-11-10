package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import fr.croustyquizz.TestcontainersConfiguration;

/** Le schéma Flyway sur un vrai PostgreSQL : contraintes et requêtes des dépôts. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DepotsDeSoireeTest {

	private static final Instant CREATION = Instant.parse("2026-11-12T19:30:00Z");

	@Autowired
	private DepotDeSoirees soirees;

	@Autowired
	private DepotDeJoueurs joueurs;

	@Autowired
	private JdbcTemplate jdbc;

	private final SecureRandom hasard = new SecureRandom();

	@Test
	void retrouveUneSoireeOuverteParSonCode() {
		Soiree soiree = soireeOuverte("K7PQ2M");

		assertThat(soirees.findByCodeAccesAndEtatIn("K7PQ2M", EtatSoiree.AVEC_CODE_ACTIF))
				.get().extracting(Soiree::getId).isEqualTo(soiree.getId());
		assertThat(soirees.findByCodeAccesAndEtatIn("ABCDEF", EtatSoiree.AVEC_CODE_ACTIF)).isEmpty();
	}

	@Test
	void deuxSoireesOuvertesNePartagentPasLeMemeCode() {
		soireeOuverte("K7PQ2M");

		assertThatThrownBy(() -> soireeOuverte("K7PQ2M")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void leCodeDUneSoireeTermineePeutResservir() {
		Soiree terminee = soireeOuverte("K7PQ2M");
		// La fin de Soirée arrive plus tard : on force l'état en base.
		jdbc.update("update soiree set etat = 'TERMINEE' where id = ?", terminee.getId());

		Soiree nouvelle = soireeOuverte("K7PQ2M");

		assertThat(soirees.findByCodeAccesAndEtatIn("K7PQ2M", EtatSoiree.AVEC_CODE_ACTIF))
				.get().extracting(Soiree::getId).isEqualTo(nouvelle.getId());
	}

	@Test
	void uneSoireeEnPreparationNAPasDeCode() {
		Soiree soiree = soirees.saveAndFlush(new Soiree("Quiz", Format.SOLO, Pilotage.MANUEL, CREATION));

		assertThatThrownBy(() -> jdbc.update("update soiree set code_acces = 'K7PQ2M' where id = ?", soiree.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void listeLesJoueursDansLeurOrdreDArrivee() {
		UUID soiree = soireeOuverte("K7PQ2M").getId();
		joueurs.saveAndFlush(new Joueur(soiree, "Tom", JetonDeReconnexion.generer(hasard), CREATION.plusSeconds(20)));
		joueurs.saveAndFlush(new Joueur(soiree, "Léa", JetonDeReconnexion.generer(hasard), CREATION.plusSeconds(10)));

		assertThat(joueurs.findBySoireeIdOrderByArriveLeAsc(soiree))
				.extracting(Joueur::getPseudo).containsExactly("Léa", "Tom");
	}

	@Test
	void neStockeQueLEmpreinteDuJeton() {
		UUID soiree = soireeOuverte("K7PQ2M").getId();
		JetonDeReconnexion jeton = JetonDeReconnexion.generer(hasard);
		Joueur joueur = joueurs.saveAndFlush(new Joueur(soiree, "Léa", jeton, CREATION));

		byte[] stocke = jdbc.queryForObject("select empreinte_jeton from joueur where id = ?", byte[].class,
				joueur.getId());

		assertThat(stocke).isEqualTo(jeton.empreinte());
	}

	private Soiree soireeOuverte(String code) {
		Soiree soiree = new Soiree("Quiz du jeudi", Format.SOLO, Pilotage.MANUEL, CREATION);
		soiree.ouvrir(code, CREATION.plusSeconds(60));
		return soirees.saveAndFlush(soiree);
	}

}
