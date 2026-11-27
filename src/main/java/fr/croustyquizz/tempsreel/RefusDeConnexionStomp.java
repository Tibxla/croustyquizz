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
 * <p>Seul un refus métier ({@link RefusDeCommande}) est définitif : le client affiche
 * son détail et ne se reconnecte pas. Une panne technique (base indisponible, par
 * exemple) reste une exception, pour que le client réessaie comme après une coupure.
 * Les refus de SUBSCRIBE et de SEND restent aussi des exceptions : la connexion survit
 * à un abonnement refusé.
 */
final class RefusDeConnexionStomp implements ChannelInterceptor {

	private final ChannelInterceptor controle;
	private final MessageChannel sortant;

	RefusDeConnexionStomp(ChannelInterceptor controle, MessageChannel sortant) {
		this.controle = controle;
		this.sortant = sortant;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel canal) {
		try {
			return controle.preSend(message, canal);
		} catch (RefusDeCommande refus) {
			if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.CONNECT) {
				throw refus;
			}
			// Le détail d'un refus métier est déjà rédigé pour l'écran.
			var erreur = StompHeaderAccessor.create(StompCommand.ERROR);
			erreur.setSessionId(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()));
			erreur.setMessage(refus.refus().detail());
			sortant.send(MessageBuilder.createMessage(new byte[0], erreur.getMessageHeaders()));
			return null;
		}
	}
}
