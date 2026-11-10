package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SoireeTest {

	private static final Instant CREATION = Instant.parse("2026-11-12T19:30:00Z");
	private static final Instant OUVERTURE = Instant.parse("2026-11-12T19:40:00Z");

	private final Soiree soiree = new Soiree("  Quiz du jeudi ", Format.SOLO, Pilotage.MANUEL, CREATION);

	@Test
	void naitEnPreparationSansCodeDAcces() {
		assertThat(soiree.getEtat()).isEqualTo(EtatSoiree.PREPARATION);
		assertThat(soiree.getCodeAcces()).isNull();
		assertThat(soiree.getTitre()).isEqualTo("Quiz du jeudi");
		assertThat(soiree.getCreeeLe()).isEqualTo(CREATION);
		assertThat(soiree.accepteLesArrivees()).isFalse();
	}

	@Test
	void sOuvreAvecSonCodeDAcces() {
		assertThat(soiree.ouvrir("K7PQ2M", OUVERTURE)).isTrue();

		assertThat(soiree.getEtat()).isEqualTo(EtatSoiree.OUVERTE);
		assertThat(soiree.getCodeAcces()).isEqualTo("K7PQ2M");
		assertThat(soiree.getOuverteLe()).isEqualTo(OUVERTURE);
		assertThat(soiree.accepteLesArrivees()).isTrue();
	}

	@Test
	void rouvrirUneSoireeOuverteNeChangeRien() {
		soiree.ouvrir("K7PQ2M", OUVERTURE);

		assertThat(soiree.ouvrir("ABCDEF", OUVERTURE.plusSeconds(60))).isFalse();

		assertThat(soiree.getCodeAcces()).isEqualTo("K7PQ2M");
		assertThat(soiree.getOuverteLe()).isEqualTo(OUVERTURE);
	}

	@Test
	void uneSoireeLanceeNePeutPlusSOuvrir() {
		soiree.ouvrir("K7PQ2M", OUVERTURE);
		// Le lancement arrive avec le déroulement des Manches : on force l'état.
		ReflectionTestUtils.setField(soiree, "etat", EtatSoiree.EN_COURS);

		assertThatThrownBy(() -> soiree.ouvrir("ABCDEF", OUVERTURE)).isInstanceOf(SoireeDejaLancee.class);
		assertThat(soiree.accepteLesArrivees()).isFalse();
	}

	@Test
	void refuseUnCodeDAccesMalForme() {
		assertThatThrownBy(() -> soiree.ouvrir("K0PQ2M", OUVERTURE)).isInstanceOf(IllegalArgumentException.class);
		assertThat(soiree.getEtat()).isEqualTo(EtatSoiree.PREPARATION);
	}

	@Test
	void refuseUnTitreVideOuTropLong() {
		assertThatThrownBy(() -> new Soiree("   ", Format.SOLO, Pilotage.MANUEL, CREATION))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Soiree("x".repeat(151), Format.SOLO, Pilotage.MANUEL, CREATION))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
