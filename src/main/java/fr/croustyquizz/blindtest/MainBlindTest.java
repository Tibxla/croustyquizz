package fr.croustyquizz.blindtest;

import java.util.Objects;
import java.util.UUID;

/**
 * Main donnée par un Buzz : le Joueur saisit sa Proposition de titre pendant {@code saisie}.
 *
 * @param tempsBuzzMs temps d'extrait joué au moment du Buzz, hors pauses, compté au départage (ADR 0009, point 12)
 * @param brouillon   dernier brouillon reçu, montré à son seul auteur et jugé à l'échéance
 */
public record MainBlindTest(UUID joueurId, String pseudo, Delai saisie, long tempsBuzzMs,
		BrouillonProposition brouillon) {

	public MainBlindTest {
		Objects.requireNonNull(joueurId, "joueurId");
		Objects.requireNonNull(pseudo, "pseudo");
		Objects.requireNonNull(saisie, "saisie");
		Objects.requireNonNull(brouillon, "brouillon");
	}

	MainBlindTest avecBrouillon(BrouillonProposition nouveau) {
		return new MainBlindTest(joueurId, pseudo, saisie, tempsBuzzMs, nouveau);
	}

	MainBlindTest avecSaisie(Delai delai) {
		return new MainBlindTest(joueurId, pseudo, delai, tempsBuzzMs, brouillon);
	}

}
