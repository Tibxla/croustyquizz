package fr.croustyquizz.manche;

/** Action programmée par une {@link Horloge}, annulable tant qu'elle n'a pas eu lieu. */
public interface Minuteur {

	/** Sans effet si l'action a déjà eu lieu ou a déjà été annulée. */
	void annuler();

}
