package fr.croustyquizz.soiree;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cycle de vie d'une Soirée : on la prépare, on l'ouvre aux Joueurs, on la lance,
 * puis elle se termine. On ne revient jamais en arrière.
 */
public enum EtatSoiree {
	PREPARATION,
	OUVERTE,
	EN_COURS,
	TERMINEE;

	/** États où le code d'accès désigne encore la Soirée. */
	public static final Set<EtatSoiree> AVEC_CODE_ACTIF = EnumSet.of(OUVERTE, EN_COURS);
}
