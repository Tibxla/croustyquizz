package fr.croustyquizz.tempsreel;

/**
 * Message poussé en temps réel vers une Manette, un Écran de salle ou la Console
 * d'animation. Chaque package déclare ses propres événements, de préférence des
 * records publics. Ils partent en JSON dans une {@link Enveloppe} qui porte leur
 * {@link #type()} à côté de leurs champs.
 */
public interface Evenement {

	/** Nom stable de l'événement, lu par le JavaScript des pages, par exemple {@code "question-posee"}. */
	String type();

}
