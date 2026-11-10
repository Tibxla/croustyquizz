package fr.croustyquizz.tempsreel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** Barrière d'installation effective, indépendante du registre interne du broker. */
@Component
public final class RegistreAbonnements implements ExecutorChannelInterceptor {
	private final Map<String, Map<UUID, String>> connexions = new HashMap<>();

	@Override
	public synchronized Message<?> beforeHandle(Message<?> message, MessageChannel canal, MessageHandler gestionnaire) {
		if (gestionnaire instanceof SimpleBrokerMessageHandler
				&& SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.CONNECT) {
			connexions.putIfAbsent(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()), new HashMap<>());
		}
		return message;
	}

	@Override
	public synchronized void afterMessageHandled(Message<?> message, MessageChannel canal,
			MessageHandler gestionnaire, Exception erreur) {
		if (!(gestionnaire instanceof SimpleBrokerMessageHandler) || erreur != null) {
			return;
		}
		String connexion = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
		SimpMessageType type = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
		if (type == SimpMessageType.DISCONNECT) {
			connexions.remove(connexion);
			return;
		}
		Map<UUID, String> abonnements = connexions.get(connexion);
		String identifiant = SimpMessageHeaderAccessor.getSubscriptionId(message.getHeaders());
		if (abonnements == null || identifiant == null) {
			return;
		}
		UUID abonnement;
		try {
			abonnement = UUID.fromString(identifiant);
		} catch (IllegalArgumentException invalide) {
			return; // Les anciens clients sans UUID ne disposent pas de capture synchronisée.
		}
		if (type == SimpMessageType.SUBSCRIBE) {
			String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
			if (destination != null && destination.startsWith("/topic/")) {
				abonnements.putIfAbsent(abonnement, destination);
			}
		} else if (type == SimpMessageType.UNSUBSCRIBE) {
			abonnements.remove(abonnement);
		}
	}

	/** Un UUID identifie une seule connexion vivante et exactement cette destination. */
	public synchronized boolean estInstalle(UUID abonnementId, String destination) {
		long correspondances = connexions.values().stream().filter(a -> a.containsKey(abonnementId)).count();
		return correspondances == 1 && connexions.values().stream()
				.anyMatch(a -> destination.equals(a.get(abonnementId)));
	}

	@EventListener
	public synchronized void deconnecter(SessionDisconnectEvent evenement) {
		connexions.remove(evenement.getSessionId());
	}
}
