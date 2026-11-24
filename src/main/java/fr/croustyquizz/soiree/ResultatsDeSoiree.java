package fr.croustyquizz.soiree;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ce que la partie commune des vues montre des résultats : le Classement
 * général pour toutes les destinations, la dernière Réponse et le résultat
 * personnel pour la Manette d'un Joueur. Une Manette rechargée les retrouve
 * ainsi par HTTP (#10).
 * <p>
 * La Soirée ne connaît ni les Questions ni les Réponses : le Mode de jeu qui les
 * garde fournit l'implémentation (le Quiz, #14). Elle est appelée sous le verrou
 * de la Soirée, pendant l'assemblage des vues, et doit respecter la
 * confidentialité du contrat ({@code VueManette} dans
 * {@code docs/api/schemas.yaml}) : ni justesse ni points avant la correction,
 * aucun résultat personnel pendant une Question ouverte ou en pause, et seules
 * les Questions corrigées comptent dans le classement. Sans implémentation, la
 * Soirée montre un classement vide et aucune Réponse ({@link ResultatsVides}).
 */
public interface ResultatsDeSoiree {

	/** Classement général de la Soirée, rang croissant ; vide tant que rien n'est corrigé. */
	List<LigneClassement> classement(UUID soireeId);

	/**
	 * Réponse enregistrée du Joueur à l'étape courante, sans justesse ni points.
	 * Absente quand la Manche active n'est pas celle du Mode de jeu qui l'a reçue
	 * (Karaoké, ou une Manche suivante) : une Réponse de Quiz ne ressort pas dans
	 * la Manche d'après.
	 */
	Optional<AccuseReponse> derniereReponse(UUID soireeId, UUID joueurId);

	/**
	 * Résultat du Joueur à la dernière étape corrigée. Absent pendant une
	 * Question ouverte, en pause ou en {@code ATTENTE} de la suivante, et quand la
	 * Manche active n'est pas celle du Mode de jeu qui l'a corrigé (Karaoké, ou une
	 * Manche suivante) : le schéma {@code VueManette} l'impose à {@code null}.
	 */
	Optional<ResultatPersonnel> resultatPersonnel(UUID soireeId, UUID joueurId);

	/** {@code LigneClassement} dans schemas.yaml. */
	record LigneClassement(UUID concurrentId, String nom, int rang, int points, int bonnesReponses, long tempsCumuleMs) {
	}

	/** {@code AccuseReponse} dans schemas.yaml : immuable, le même envoi retrouve exactement cet accusé. */
	record AccuseReponse(UUID envoiId, UUID reponseId, UUID etapeId, UUID propositionId, Instant recueLe) {
	}

	/** {@code ResultatPersonnel} dans schemas.yaml. */
	record ResultatPersonnel(UUID etapeId, boolean juste, int pointsGagnes) {
	}

}
