package fr.croustyquizz.soiree;

/** Décide si un pseudo peut s'afficher sur l'Écran de salle. */
@FunctionalInterface
public interface FiltreDePseudos {

	boolean estAcceptable(String pseudo);

}
