package fr.croustyquizz.soiree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class JoueurTest {

	@Test
	void laCleDePseudoConfondAccentsCasseEtEspaces() {
		assertThat(Joueur.cleDePseudo("Léa")).isEqualTo(Joueur.cleDePseudo("LEA")).isEqualTo("lea");
		assertThat(Joueur.cleDePseudo("  Jean   Luc ")).isEqualTo("jean luc");
		assertThat(Joueur.cleDePseudo("한글")).isEqualTo("한글");
	}

	@Test
	void laCleDePseudoConfondLesEspacesInsecables() {
		// Insécable (U+00A0), insécable fine (U+202F) : un clavier de téléphone peut les produire.
		assertThat(Joueur.cleDePseudo("Jean Luc")).isEqualTo(Joueur.cleDePseudo("Jean Luc")).isEqualTo("jean luc");
		assertThat(Joueur.cleDePseudo(" Léa ")).isEqualTo("lea");
	}

	@Test
	void unPseudoFaitDEspacesInsecablesEstVide() {
		assertThatThrownBy(() -> new Joueur(SOIREE, "   ", jeton, ARRIVEE)).isInstanceOf(PseudoInvalide.class);
		assertThat(new Joueur(SOIREE, " Léa ", jeton, ARRIVEE).getPseudo()).isEqualTo("Léa");
	}

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
		String trenteEmojis = "🎤".repeat(30);

		assertThat(new Joueur(SOIREE, trenteEmojis, jeton, ARRIVEE).getPseudo()).isEqualTo(trenteEmojis);
		assertThatThrownBy(() -> new Joueur(SOIREE, "a".repeat(31), jeton, ARRIVEE))
				.isInstanceOf(PseudoInvalide.class);
	}

}
