package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class JoueurTest {

	private static final UUID SOIREE = UUID.randomUUID();
	private static final Instant ARRIVEE = Instant.parse("2026-11-12T19:41:07Z");

	private final JetonDeReconnexion jeton = JetonDeReconnexion.generer(new SecureRandom());

	@Test
	void arriveAvecSonPseudoSansEspacesAutour() {
		Joueur joueur = new Joueur(SOIREE, "  Léa ", jeton, ARRIVEE);

		assertThat(joueur.getPseudo()).isEqualTo("Léa");
		assertThat(joueur.getSoireeId()).isEqualTo(SOIREE);
		assertThat(joueur.getArriveLe()).isEqualTo(ARRIVEE);
		assertThat(joueur.isExclu()).isFalse();
	}

	@Test
	void refuseUnPseudoVide() {
		assertThatThrownBy(() -> new Joueur(SOIREE, "   ", jeton, ARRIVEE)).isInstanceOf(PseudoInvalide.class);
		assertThatThrownBy(() -> new Joueur(SOIREE, null, jeton, ARRIVEE)).isInstanceOf(PseudoInvalide.class);
	}

	@Test
	void compteLesCaracteresEtPasLesUnitesUtf16() {
		String vingtEmojis = "🎤".repeat(20);

		assertThat(new Joueur(SOIREE, vingtEmojis, jeton, ARRIVEE).getPseudo()).isEqualTo(vingtEmojis);
		assertThatThrownBy(() -> new Joueur(SOIREE, "a".repeat(21), jeton, ARRIVEE))
				.isInstanceOf(PseudoInvalide.class);
	}

}
