package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import fr.croustyquizz.TestcontainersConfiguration;

/**
 * La migration des pseudos sur des Joueurs arrivés avant elle : leurs pseudos
 * sont réservés comme ceux des nouveaux, et seuls d'anciens doublons gardent une
 * clé qui ne bloque personne. Joué dans un schéma à part, pour ne pas toucher au
 * schéma des autres tests.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MigrationPseudoUniqueTest {

	private static final String SCHEMA = "migration_pseudo_unique";
	private static final String AVANT = "202611231901";

	@Autowired
	private DataSource source;

	@Autowired
	private JdbcTemplate jdbc;

	private final SecureRandom hasard = new SecureRandom();

	@AfterEach
	void effacerLeSchema() {
		jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
	}

	@Test
	void lesPseudosDejaPresentsSontReservesEtSeulsLesDoublonsGardentUneCleUnique() {
		migrer(AVANT);
		UUID soiree = soiree();
		UUID lea = joueur(soiree, "Léa", "2026-11-12T19:41:00Z");
		UUID leaEnDouble = joueur(soiree, "LEA", "2026-11-12T19:42:00Z");
		UUID jeanLuc = joueur(soiree, "Jean  Luc", "2026-11-12T19:43:00Z");
		UUID noel = joueur(soiree, "Noël Œdipe", "2026-11-12T19:44:00Z");
		UUID ailleurs = joueur(soiree(), "Léa", "2026-11-12T19:45:00Z");

		migrer(null);

		Map<UUID, String> cles = jdbc.queryForList("select id, pseudo_normalise from " + SCHEMA + ".joueur").stream()
				.collect(Collectors.toMap(ligne -> (UUID) ligne.get("id"), ligne -> (String) ligne.get("pseudo_normalise")));
		// Les clés suivent Joueur.cleDePseudo : un nouvel arrivant « lea » ou « Jean Luc » serait refusé.
		assertThat(cles.get(lea)).isEqualTo(Joueur.cleDePseudo("Léa"));
		assertThat(cles.get(jeanLuc)).isEqualTo(Joueur.cleDePseudo("Jean  Luc"));
		assertThat(cles.get(noel)).isEqualTo(Joueur.cleDePseudo("Noël Œdipe"));
		assertThat(cles.get(ailleurs)).isEqualTo(Joueur.cleDePseudo("Léa"));
		// Le second « Léa » de la même Soirée, arrivé après, ne peut pas prendre la même clé.
		assertThat(cles.get(leaEnDouble)).isEqualTo("#" + leaEnDouble);
	}

	private void migrer(String cible) {
		var configuration = Flyway.configure().dataSource(source).schemas(SCHEMA).locations("classpath:db/migration");
		if (cible != null) {
			configuration.target(cible);
		}
		configuration.load().migrate();
	}

	private UUID soiree() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into " + SCHEMA + ".soiree (id, titre, format, pilotage, etat, creee_le) "
				+ "values (?, 'Quiz', 'SOLO', 'MANUEL', 'PREPARATION', now())", id);
		return id;
	}

	private UUID joueur(UUID soiree, String pseudo, String arriveLe) {
		UUID id = UUID.randomUUID();
		byte[] empreinte = new byte[32];
		hasard.nextBytes(empreinte);
		jdbc.update("insert into " + SCHEMA + ".joueur (id, soiree_id, pseudo, empreinte_jeton, arrive_le) "
				+ "values (?, ?, ?, ?, ?::timestamptz)", id, soiree, pseudo, empreinte, arriveLe);
		return id;
	}

}
