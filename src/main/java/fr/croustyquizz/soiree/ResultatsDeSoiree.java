package fr.croustyquizz.soiree;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Dernière Réponse et résultat personnel dans la partie commune de la Manette.
 * Une Manette rechargée les retrouve ainsi par HTTP (#10).
 * <p>
 * La Soirée ne connaît ni les Questions ni les Réponses : le Mode de jeu qui les
 * garde fournit l'implémentation (le Quiz, #14). Elle est appelée sous le verrou
 * de la Soirée, pendant l'assemblage des vues, et doit respecter la
 * confidentialité du contrat ({@code VueManette} dans
 * {@code docs/api/schemas.yaml}) : ni justesse ni points avant la correction,
 * aucun résultat personnel pendant une Question ouverte ou en pause. Le
 * Classement général suit le port {@link ClassementDeSoiree}.
 */
public interface ResultatsDeSoiree {

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

	/** {@code AccuseReponse} dans schemas.yaml : immuable, le même envoi retrouve exactement cet accusé. */
	record AccuseReponse(UUID envoiId, UUID reponseId, UUID etapeId, UUID propositionId, Instant recueLe) {
	}

	/** {@code ResultatPersonnel} dans schemas.yaml. */
	record ResultatPersonnel(UUID etapeId, boolean juste, int pointsGagnes) {
	}

}
