package fr.croustyquizz.manche;

import java.util.List;

/**
 * Une Manche en train de se jouer. Chaque Mode de jeu fournit son implémentation,
 * créée par sa {@link FabriqueDeDeroulement} : un objet par Manche, donc plusieurs
 * Soirées peuvent jouer en même temps sans partager d'état.
 *
 * <h2>Règle de concurrence</h2>
 * Le socle appelle toutes les méthodes d'un même déroulement depuis un seul fil,
 * minuteurs de l'{@link Horloge} compris. Une implémentation n'a donc pas besoin
 * d'être thread-safe, et ne doit créer ni thread ni minuteur par elle-même.
 *
 * <h2>Cycle de vie</h2>
 * {@code demarrer} est appelé une fois. Ensuite la Manche avance seule avec ses
 * minuteurs, ou sur ordre de l'Animateur ({@code passerEtape}). Quand elle arrive
 * au bout d'elle-même, elle appelle {@link ContexteDeManche#signalerFin()} une seule
 * fois. Après {@code terminer} ou la fin naturelle, toute action est ignorée.
 */
public interface DeroulementDeManche {

	void demarrer();

	/** Action d'un Joueur. Une action inconnue du mode ou arrivée hors délai est ignorée. */
	void recevoir(ActionJoueur action);

	/** Gèle les minuteurs en cours. Sans effet si la Manche est déjà en pause ou terminée. */
	void mettreEnPause();

	/** Relance les minuteurs avec le temps qu'il leur restait. */
	void reprendre();

	/** Clôt l'étape en cours (Question, Morceau, Passage) et passe à la suivante. */
	void passerEtape();

	/** Arrête la Manche sur ordre de l'Animateur, sans appeler {@code signalerFin}. */
	void terminer();

	boolean estTerminee();

	/** Points attribués depuis le début de la Manche, jamais {@code null}. */
	List<AttributionDePoints> points();

}
