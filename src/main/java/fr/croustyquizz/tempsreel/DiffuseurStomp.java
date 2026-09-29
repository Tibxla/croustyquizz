package fr.croustyquizz.tempsreel;

import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Diffuseur réel : publie chaque événement, dans son {@link Enveloppe}, sur le bon canal STOMP. */
@Component
class DiffuseurStomp implements DiffuseurTempsReel {

	private final SimpMessagingTemplate messagerie;

	DiffuseurStomp(SimpMessagingTemplate messagerie) {
		this.messagerie = messagerie;
	}

	@Override
	public void diffuserSalle(UUID soireeId, Evenement evenement) {
		messagerie.convertAndSend(Canaux.salle(soireeId), Enveloppe.de(evenement));
	}

	@Override
	public void envoyerJoueur(UUID joueurId, Evenement evenement) {
		messagerie.convertAndSend(Canaux.joueur(joueurId), Enveloppe.de(evenement));
	}

	@Override
	public void envoyerConsole(UUID soireeId, Evenement evenement) {
		messagerie.convertAndSend(Canaux.console(soireeId), Enveloppe.de(evenement));
	}

}
