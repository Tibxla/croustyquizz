package fr.croustyquizz.blindtest;

import java.util.Objects;

/**
 * Champ d'une Proposition de titre jugée : le texte saisi et son verdict (contrat : ChampJuge).
 *
 * @param saisie  texte tapé par le Joueur, éventuellement vide
 * @param verdict verdict de la correction, ou celui fixé par l'Animateur
 * @param arbitre vrai si l'Animateur a renversé le verdict de la correction (ADR 0009, point 15)
 */
public record ChampJuge(String saisie, VerdictChamp verdict, boolean arbitre) {

	public ChampJuge {
		Objects.requireNonNull(saisie, "saisie");
		Objects.requireNonNull(verdict, "verdict");
		if (verdict == VerdictChamp.NON_DEMANDE && arbitre) {
			throw new IllegalArgumentException("Un champ non demandé ne s'arbitre pas.");
		}
	}

	static ChampJuge juge(String saisie, VerdictChamp verdict) {
		return new ChampJuge(saisie, verdict, false);
	}

	/** Verdict fixé par l'Animateur ; revenir au verdict de la correction efface la marque d'arbitrage. */
	ChampJuge renverse() {
		VerdictChamp inverse = verdict == VerdictChamp.JUSTE ? VerdictChamp.FAUX : VerdictChamp.JUSTE;
		return new ChampJuge(saisie, inverse, !arbitre);
	}

	boolean estVide() {
		return saisie.isBlank();
	}

}
