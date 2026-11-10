package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class CodeAccesTest {

	@RepeatedTest(20)
	void unCodeTireEstToujoursValide() {
		String code = CodeAcces.generer(new Random());

		assertThat(code).hasSize(6).doesNotContain("0", "O", "1", "I");
		assertThat(CodeAcces.estValide(code)).isTrue();
	}

	@Test
	void unCodeTapeALaMainEstRemisEnForme() {
		assertThat(CodeAcces.normaliser(" k7p q2m ")).contains("K7PQ2M");
	}

	@Test
	void uneSaisieQuiNePeutPasEtreUnCodeEstRejetee() {
		assertThat(CodeAcces.normaliser(null)).isEmpty();
		assertThat(CodeAcces.normaliser("K7PQ2")).isEmpty();
		assertThat(CodeAcces.normaliser("K7PQ2MX")).isEmpty();
		assertThat(CodeAcces.normaliser("K0PQ2M")).isEmpty();
	}

}
