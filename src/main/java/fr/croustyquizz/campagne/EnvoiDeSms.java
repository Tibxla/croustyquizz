package fr.croustyquizz.campagne;

/** Port d’envoi d’un SMS à un numéro au format international. */
@FunctionalInterface
public interface EnvoiDeSms {
	Resultat envoyer(String texte, String numeroInternational);

	default Mode mode() {
		return Mode.SIMULE;
	}

	enum Mode { SIMULE, TWILIO, TWILIO_ESSAI }

	sealed interface Resultat permits Envoye, Echec { }

	record Envoye(String identifiantPrestataire) implements Resultat { }

	/** Le motif est affichable et ne contient ni numéro ni lien personnel. */
	record Echec(String motif) implements Resultat { }
}
