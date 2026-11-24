package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.AttributionDePoints;
import java.util.List;

/**
 * État privé du mode, persisté par le service : la sélection tirée y vit, jamais dans les réglages publics
 * (ADR 0009, point 1).
 *
 * @param selection Morceaux tirés, dans l'ordre de jeu
 * @param courant   Morceau courant, nul avant le démarrage
 * @param points    points attribués depuis le début de la Manche
 */
public record EtatBlindTestPersiste(List<Morceau> selection, MorceauEnCours courant, List<AttributionDePoints> points) {

	public EtatBlindTestPersiste {
		selection = List.copyOf(selection);
		points = List.copyOf(points);
		if (selection.isEmpty() || selection.size() > ReglagesBlindTest.NOMBRE_MAX_MORCEAUX) {
			throw new IllegalArgumentException("Une Manche de Blind test compte de 1 à 50 Morceaux.");
		}
	}

}
