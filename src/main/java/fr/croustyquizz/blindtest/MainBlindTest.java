package fr.croustyquizz.blindtest;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Main donnée par un Buzz : le Joueur saisit sa Proposition de titre pendant {@code saisie}.
 *
 * @param tempsBuzzMs     temps d'extrait joué au moment du Buzz, hors pauses, compté au départage (ADR 0009, point 12)
 * @param brouillon       dernier brouillon retenu, montré à son seul auteur et jugé à l'échéance
 * @param numeroBrouillon numéro d'envoi du brouillon retenu, 0 avant le premier
 */
public record MainBlindTest(UUID joueurId, String pseudo, Delai saisie, long tempsBuzzMs,
		BrouillonProposition brouillon, long numeroBrouillon) {

	public MainBlindTest {
		Objects.requireNonNull(joueurId, "joueurId");
		Objects.requireNonNull(pseudo, "pseudo");
		Objects.requireNonNull(saisie, "saisie");
		Objects.requireNonNull(brouillon, "brouillon");
		if (numeroBrouillon < 0) {
			throw new IllegalArgumentException("Le numéro d'un brouillon n'est jamais négatif.");
		}
	}

	static MainBlindTest donnee(UUID joueurId, String pseudo, Delai saisie, long tempsBuzzMs) {
		return new MainBlindTest(joueurId, pseudo, saisie, tempsBuzzMs, BrouillonProposition.VIDE, 0);
	}

	/** Un brouillon plus ancien que le brouillon retenu ne le remplace pas : un rejeu en retard est ignoré. */
	boolean retiendrait(long numero) {
		return numero > numeroBrouillon;
	}

	MainBlindTest avecBrouillon(long numero, BrouillonProposition nouveau) {
		return new MainBlindTest(joueurId, pseudo, saisie, tempsBuzzMs, nouveau, numero);
	}

	MainBlindTest avecSaisie(Delai delai) {
		return new MainBlindTest(joueurId, pseudo, delai, tempsBuzzMs, brouillon, numeroBrouillon);
	}

	/** L'échéance est atteinte : la saisie court et il ne reste plus rien, même si le rappel n'est pas encore passé. */
	boolean echueA(Instant maintenant) {
		return !saisie.estGele() && saisie.restantA(maintenant).isZero();
	}

}
