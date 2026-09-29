package fr.croustyquizz.tempsreel;

/**
 * Message poussé en temps réel vers une Manette, un Écran de salle ou la Console
 * d'animation. Chaque package déclare ses propres événements (de préférence des
 * records), sérialisés en JSON avec leur {@link #type()}.
 */
public interface Evenement {

	/** Nom stable de l'événement, lu par le JavaScript des pages, par exemple {@code "question-posee"}. */
	String type();

}
