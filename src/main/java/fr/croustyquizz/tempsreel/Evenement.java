package fr.croustyquizz.tempsreel;

/**
 * Vue complète immuable poussée en temps réel vers une Manette, un Écran de salle ou la Console
 * d'animation. Chaque package déclare ses propres événements, de préférence des
 * records publics contenant des collections immuables. Aucun delta. Ils partent en
 * JSON dans une {@link Enveloppe} qui porte leur
 * {@link #type()} à côté de leurs champs.
 */
@FunctionalInterface
public interface Evenement {

	/** Nom stable de l'événement, lu par le JavaScript des pages, par exemple {@code "question-posee"}. */
	String type();

}
