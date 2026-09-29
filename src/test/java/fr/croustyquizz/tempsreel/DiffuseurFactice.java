package fr.croustyquizz.tempsreel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Diffuseur de test : enregistre chaque envoi au lieu de l'envoyer. */
public final class DiffuseurFactice implements DiffuseurTempsReel {

	public enum Cible {
		SALLE, JOUEUR, CONSOLE
	}

	public record Envoi(Cible cible, UUID destinataire, Evenement evenement) {
	}

	private final List<Envoi> envois = new ArrayList<>();

	@Override
	public void diffuserSalle(UUID soireeId, Evenement evenement) {
		envois.add(new Envoi(Cible.SALLE, soireeId, evenement));
	}

	@Override
	public void envoyerJoueur(UUID joueurId, Evenement evenement) {
		envois.add(new Envoi(Cible.JOUEUR, joueurId, evenement));
	}

	@Override
	public void envoyerConsole(UUID soireeId, Evenement evenement) {
		envois.add(new Envoi(Cible.CONSOLE, soireeId, evenement));
	}

	public List<Envoi> envois() {
		return List.copyOf(envois);
	}

	/** Types des événements diffusés à la salle, dans l'ordre. */
	public List<String> typesSalle() {
		return envois.stream()
				.filter(envoi -> envoi.cible() == Cible.SALLE)
				.map(envoi -> envoi.evenement().type())
				.toList();
	}

	/** Événements reçus par la Manette d'un Joueur, dans l'ordre. */
	public List<Evenement> recusPar(UUID joueurId) {
		return envois.stream()
				.filter(envoi -> envoi.cible() == Cible.JOUEUR && envoi.destinataire().equals(joueurId))
				.map(Envoi::evenement)
				.toList();
	}

}
