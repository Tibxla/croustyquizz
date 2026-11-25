package fr.croustyquizz.manche;

import java.util.UUID;

/**
 * Points gagnés par un Concurrent (Joueur en Solo, Équipe en Format Équipe) pendant une Manche.
 *
 * <p>Les deux derniers champs servent au départage du Classement général (#24) : « nombre de bonnes réponses, puis
 * rapidité ». Un Mode de jeu marque {@code bonneReponse} sur <strong>au plus une</strong> attribution par Concurrent et
 * par étape (Question ou Morceau), même s'il en produit plusieurs pour cette étape (titre et artiste au Blind test),
 * pour qu'une étape ne compte jamais deux bonnes réponses ni deux fois le même temps.
 *
 * @param concurrentId identifiant du Joueur ou de l'Équipe
 * @param valeur       nombre de points, positif
 * @param motif        raison lisible, par exemple « bonne réponse en 3 s »
 * @param bonneReponse vrai si cette attribution compte une bonne réponse au départage
 * @param tempsMs      temps compté pour la rapidité (réponse au Quiz, Buzz gagnant au Blind test), en millisecondes,
 *                     hors pauses ; zéro quand l'attribution ne compte pas de bonne réponse
 */
public record AttributionDePoints(UUID concurrentId, int valeur, String motif, boolean bonneReponse, long tempsMs) {

	public AttributionDePoints {
		if (tempsMs < 0) {
			throw new IllegalArgumentException("Le temps de départage ne peut pas être négatif.");
		}
		if (!bonneReponse && tempsMs != 0) {
			throw new IllegalArgumentException("Seule une bonne réponse porte un temps de départage.");
		}
	}

	/** Attribution qui ne compte pas au départage : Karaoké, bonus, ou Mode de jeu qui ne le renseigne pas encore. */
	public AttributionDePoints(UUID concurrentId, int valeur, String motif) {
		this(concurrentId, valeur, motif, false, 0);
	}
}
