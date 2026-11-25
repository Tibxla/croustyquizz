package fr.croustyquizz.tempsreel;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;

import fr.croustyquizz.manche.RefusDeCommande;

/**
 * Répond à un CONNECT refusé par une trame ERROR, puis par la fermeture (#241).
 *
 * <p>Avec {@code setPreserveReceiveOrder(true)}, Spring confie chaque trame reçue à
 * {@code OrderedMessageChannelDecorator}, qui journalise l'exception d'un intercepteur
 * puis l'avale : {@code StompSubProtocolHandler} n'envoie alors ni ERROR ni fermeture,
 * et le client attend sans réponse. Ce garde attrape le refus du CONNECT, publie la
 * trame ERROR sur le canal sortant (le gestionnaire STOMP ferme la session après
 * l'avoir écrite) et retire le CONNECT.
 *
 * <p>Les refus de SUBSCRIBE et de SEND restent des exceptions : la connexion survit
 * à un abonnement refusé.
 */
final class RefusDeConnexionStomp implements ChannelInterceptor {

	static final String MOTIF_PAR_DEFAUT = "La connexion en temps réel a été refusée. Recharge la page.";

	private final ChannelInterceptor controle;
	private final MessageChannel sortant;

	RefusDeConnexionStomp(ChannelInterceptor controle, MessageChannel sortant) {
		this.controle = controle;
		this.sortant = sortant;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel canal) {
		if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.CONNECT) {
			return controle.preSend(message, canal);
		}
		try {
			return controle.preSend(message, canal);
		} catch (RuntimeException refus) {
			var erreur = StompHeaderAccessor.create(StompCommand.ERROR);
			erreur.setSessionId(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()));
			erreur.setMessage(motif(refus));
			sortant.send(MessageBuilder.createMessage(new byte[0], erreur.getMessageHeaders()));
			return null;
		}
	}

	/** Le détail d'un refus métier est déjà rédigé pour l'écran ; une autre panne n'expose rien de technique. */
	static String motif(Throwable refus) {
		for (Throwable cause = refus; cause != null; cause = cause.getCause()) {
			if (cause instanceof RefusDeCommande metier) {
				return metier.refus().detail();
			}
		}
		return MOTIF_PAR_DEFAUT;
	}
}
