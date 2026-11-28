package fr.croustyquizz.blindtest;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Main donnée par un Buzz : le Joueur saisit sa Proposition de titre pendant {@code saisie}.
 *
 * @param tempsBuzzMs     temps d'extrait joué au moment du Buzz, hors pauses, compté au départage (ADR 0009, point 12)
 * @param brouillon       dernier brouillon retenu, montré à son seul auteur et jugé à l'échéance
 * @param numeroBrouillon numéro d'envoi du brouillon retenu, 0 avant le premier
 * @param demandees       parties que demandait le formulaire au Buzz ; nul dans un état enregistré avant #294
 */
public record MainBlindTest(UUID joueurId, String pseudo, Delai saisie, long tempsBuzzMs,
		BrouillonProposition brouillon, long numeroBrouillon, Set<PartieDuMorceau> demandees) {

	public MainBlindTest {
		Objects.requireNonNull(joueurId, "joueurId");
		Objects.requireNonNull(pseudo, "pseudo");
		Objects.requireNonNull(saisie, "saisie");
		Objects.requireNonNull(brouillon, "brouillon");
		if (numeroBrouillon < 0) {
			throw new IllegalArgumentException("Le numéro d'un brouillon n'est jamais négatif.");
		}
		if (demandees != null) {
			demandees = Set.copyOf(demandees);
		}
	}

	static MainBlindTest donnee(UUID joueurId, String pseudo, Delai saisie, long tempsBuzzMs,
			Set<PartieDuMorceau> demandees) {
		return new MainBlindTest(joueurId, pseudo, saisie, tempsBuzzMs, BrouillonProposition.VIDE, 0,
				Objects.requireNonNull(demandees, "demandees"));
	}

	/**
	 * Parties jugées sur cette Proposition : celles que son formulaire demandait et qui restent à trouver. Une partie
	 * rouverte par un arbitrage pendant la saisie n'est pas jugée ; une partie accordée entre-temps ne l'est plus.
	 */
	Set<PartieDuMorceau> demandeesParmi(Set<PartieDuMorceau> encoreDemandees) {
		Set<PartieDuMorceau> jugees = EnumSet.noneOf(PartieDuMorceau.class);
		jugees.addAll(encoreDemandees);
		if (demandees != null) {
			jugees.retainAll(demandees);
		}
		return jugees;
	}

	/** Un brouillon plus ancien que le brouillon retenu ne le remplace pas : un rejeu en retard est ignoré. */
	boolean retiendrait(long numero) {
		return numero > numeroBrouillon;
	}

	MainBlindTest avecBrouillon(long numero, BrouillonProposition nouveau) {
		return new MainBlindTest(joueurId, pseudo, saisie, tempsBuzzMs, nouveau, numero, demandees);
	}

	MainBlindTest avecSaisie(Delai delai) {
		return new MainBlindTest(joueurId, pseudo, delai, tempsBuzzMs, brouillon, numeroBrouillon, demandees);
	}

	/** L'échéance est atteinte : la saisie court et il ne reste plus rien, même si le rappel n'est pas encore passé. */
	boolean echueA(Instant maintenant) {
		return !saisie.estGele() && saisie.restantA(maintenant).isZero();
	}

}
