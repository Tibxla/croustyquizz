package fr.croustyquizz.campagne;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CreneauxSmsTest {

	@ParameterizedTest
	@CsvSource({
		"2026-10-24T05:59:59Z, 2026-10-24T06:00:00Z",
		"2026-10-24T06:00:00Z, 2026-10-24T06:00:00Z",
		"2026-10-24T17:59:59Z, 2026-10-24T17:59:59Z",
		"2026-10-24T18:00:00Z, 2026-10-26T07:00:00Z",
		"2026-03-29T10:00:00Z, 2026-03-30T06:00:00Z",
		"2026-04-04T18:00:00Z, 2026-04-07T06:00:00Z",
		"2025-12-31T19:00:00Z, 2026-01-02T07:00:00Z"
	})
	void reporteAuProchainCreneauEnHeureDeParis(String demande, String attendu) {
		assertThat(CreneauxSms.prochain(Instant.parse(demande))).isEqualTo(Instant.parse(attendu));
	}

	@ParameterizedTest
	@CsvSource({
		"2026-01-01, 2026-01-02", "2027-03-29, 2027-03-30", "2026-05-01, 2026-05-02",
		"2026-05-08, 2026-05-09", "2026-05-14, 2026-05-15", "2026-05-25, 2026-05-26",
		"2026-07-14, 2026-07-15", "2026-08-15, 2026-08-17", "2025-11-01, 2025-11-03",
		"2026-11-11, 2026-11-12", "2026-12-25, 2026-12-26"
	})
	void exclutLesJoursFeries(String ferie, String reprise) {
		var paris = ZoneId.of("Europe/Paris");
		Instant demande = LocalDate.parse(ferie).atTime(14, 0).atZone(paris).toInstant();
		Instant attendu = LocalDate.parse(reprise).atTime(8, 0).atZone(paris).toInstant();
		assertThat(CreneauxSms.prochain(demande)).isEqualTo(attendu);
	}
}
