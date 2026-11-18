package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

class JetonDeReconnexionTest {

	private final SecureRandom hasard = new SecureRandom();

	@Test
	void deuxJetonsSontDifferents() {
		assertThat(JetonDeReconnexion.generer(hasard)).isNotEqualTo(JetonDeReconnexion.generer(hasard));
	}

	@Test
	void sonEmpreinteEstStable() {
		JetonDeReconnexion jeton = JetonDeReconnexion.generer(hasard);

		assertThat(jeton.valeur()).hasSize(43);
		assertThat(jeton.empreinte()).hasSize(32).isEqualTo(new JetonDeReconnexion(jeton.valeur()).empreinte());
	}

	@Test
	void neFuitPasDansLesJournaux() {
		JetonDeReconnexion jeton = JetonDeReconnexion.generer(hasard);

		assertThat(jeton.toString()).doesNotContain(jeton.valeur());
	}

}
