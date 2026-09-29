package fr.croustyquizz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DemarrageApplicationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void appliqueToutesLesMigrationsSurUnPostgresVierge() {
		Integer echecs = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where not success", Integer.class);
		Integer appliquees = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where success", Integer.class);

		assertThat(echecs).isZero();
		assertThat(appliquees).isPositive();
	}

}
