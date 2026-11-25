package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class SeauDeJetonsTest {

	private static final long SECONDE = Duration.ofSeconds(1).toNanos();
	private static final long DEPART = 1_000 * SECONDE;

	@Test
	void unSeauNeufAccepteSaRafalePuisRefuseEnDisantCombienAttendre() {
		var seau = new SeauDeJetons(3, Duration.ofMillis(200), DEPART);

		assertThat(seau.prendre(DEPART)).isZero();
		assertThat(seau.prendre(DEPART)).isZero();
		assertThat(seau.prendre(DEPART)).isZero();

		assertThat(seau.prendre(DEPART)).isEqualTo(Duration.ofMillis(200).toNanos());
	}

	@Test
	void unJetonRevientAChaqueIntervalle() {
		var seau = new SeauDeJetons(2, Duration.ofSeconds(3), DEPART);
		seau.prendre(DEPART);
		seau.prendre(DEPART);

		assertThat(seau.prendre(DEPART + SECONDE)).isEqualTo(2 * SECONDE);
		assertThat(seau.prendre(DEPART + 3 * SECONDE)).isZero();
		assertThat(seau.prendre(DEPART + 3 * SECONDE)).isEqualTo(3 * SECONDE);
	}

	@Test
	void unRefusNeConsommeRien() {
		var seau = new SeauDeJetons(1, Duration.ofSeconds(1), DEPART);
		seau.prendre(DEPART);
		for (int i = 0; i < 50; i++) {
			assertThat(seau.prendre(DEPART)).isPositive();
		}

		assertThat(seau.prendre(DEPART + SECONDE)).isZero();
	}

	@Test
	void unJetonRenduPeutResservirAussitot() {
		var seau = new SeauDeJetons(1, Duration.ofSeconds(3), DEPART);
		seau.prendre(DEPART);
		seau.rendre();

		assertThat(seau.prendre(DEPART)).isZero();
	}

	@Test
	void unSeauLongtempsInactifNeDepassePasSaRafale() {
		var seau = new SeauDeJetons(2, Duration.ofSeconds(1), DEPART);
		long bienPlusTard = DEPART + 3_600 * SECONDE;

		assertThat(seau.prendre(bienPlusTard)).isZero();
		assertThat(seau.prendre(bienPlusTard)).isZero();
		assertThat(seau.prendre(bienPlusTard)).isPositive();
	}

	@Test
	void unSeauEstPleinQuandToutesSesRafalesSontRevenues() {
		var seau = new SeauDeJetons(2, Duration.ofSeconds(1), DEPART);
		assertThat(seau.estPlein(DEPART)).isTrue();

		seau.prendre(DEPART);
		seau.prendre(DEPART);
		assertThat(seau.estPlein(DEPART + SECONDE)).isFalse();
		assertThat(seau.estPlein(DEPART + 2 * SECONDE)).isTrue();
	}

	@Test
	void uneHorlogeQuiReculeNeRendPasDeJetons() {
		var seau = new SeauDeJetons(1, Duration.ofSeconds(1), DEPART);
		seau.prendre(DEPART);

		assertThat(seau.prendre(DEPART - 10 * SECONDE)).isPositive();
	}

	@Test
	void consulterNePrendRien() {
		var seau = new SeauDeJetons(1, Duration.ofSeconds(3), DEPART);
		assertThat(seau.attente(DEPART)).isZero();
		assertThat(seau.attente(DEPART)).isZero();

		seau.prendre(DEPART);
		assertThat(seau.attente(DEPART + SECONDE)).isEqualTo(2 * SECONDE);
	}

	@Test
	void unDebitApresCoupEndetteLeSeauSansJamaisRefuser() {
		var seau = new SeauDeJetons(2, Duration.ofSeconds(3), DEPART);
		for (int i = 0; i < 5; i++) {
			seau.debiter(DEPART);
		}

		assertThat(seau.attente(DEPART)).isEqualTo(12 * SECONDE);
		assertThat(seau.attente(DEPART + 12 * SECONDE)).isZero();
	}

}
