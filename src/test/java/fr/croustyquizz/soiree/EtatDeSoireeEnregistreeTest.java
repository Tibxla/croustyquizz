package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import fr.croustyquizz.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import fr.croustyquizz.manche.EtatDeSoiree;

/** L'état d'une Soirée enregistrée se lit en base (#111) ; une Soirée inconnue n'est ni en cours ni terminée. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, EtatDeSoireeEnregistree.class})
class EtatDeSoireeEnregistreeTest {

	@Autowired
	private EtatDeSoiree etat;

	@Autowired
	private DepotDeSoirees soirees;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager entites;

	@Test
	void suitLEtatEnregistreDeLaSoiree() {
		UUID id = soirees.saveAndFlush(new Soiree("Karaoké du vendredi", Format.SOLO, Pilotage.MANUEL,
				Instant.parse("2026-11-19T19:30:00Z"))).getId();
		assertThat(etat.estEnCours(id)).isFalse();
		assertThat(etat.estTerminee(id)).isFalse();

		jdbc.update("update soiree set etat = 'EN_COURS', code_acces = 'K7PQ2M', ouverte_le = now(), debutee_le = now() where id = ?", id);
		entites.clear();
		assertThat(etat.estEnCours(id)).isTrue();
		assertThat(etat.estTerminee(id)).isFalse();

		jdbc.update("update soiree set etat = 'TERMINEE', terminee_le = now() where id = ?", id);
		entites.clear();
		assertThat(etat.estEnCours(id)).isFalse();
		assertThat(etat.estTerminee(id)).isTrue();
	}

	@Test
	void uneSoireeAbsenteSansRepliNEstNiEnCoursNiTerminee() {
		UUID inconnue = UUID.randomUUID();
		assertThat(etat.estEnCours(inconnue)).isFalse();
		assertThat(etat.estTerminee(inconnue)).isFalse();
	}

}
