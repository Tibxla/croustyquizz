package fr.croustyquizz.tempsreel;

/**
 * Forme JSON de tout message poussé aux pages :
 * {@code {"type": "buzz-recu", "donnees": {...}}}. Le JavaScript aiguille sur {@code type}.
 */
public record Enveloppe(String type, Evenement donnees) {

	public static Enveloppe de(Evenement evenement) {
		return new Enveloppe(evenement.type(), evenement);
	}

}
