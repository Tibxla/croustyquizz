package fr.croustyquizz.tempsreel;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/** Identifie CONNECT et autorise chaque destination ; seul le prototype dev/recette reste anonyme. */
@Component
public final class ControleAccesStomp implements ChannelInterceptor {
	private final ResolveurDActeur resolveur;
	private final boolean prototype;
	public ControleAccesStomp(ResolveurDActeur resolveur, Environment environnement) {
		this.resolveur = resolveur;
		prototype = environnement.acceptsProfiles(Profiles.of("dev", "recette"));
	}
	@Override
	public Message<?> preSend(Message<?> message, MessageChannel canal) {
		var entetes = MessageHeaderAccessor.getAccessor(message, SimpMessageHeaderAccessor.class);
		if (entetes == null) { return message; }
		if (!entetes.isMutable()) { entetes = SimpMessageHeaderAccessor.wrap(message); }
		if (entetes.getMessageType() == SimpMessageType.CONNECT
				&& (!prototype || entetes.getFirstNativeHeader("Authorization") != null)) {
			entetes.setUser(new IdentiteStomp(resolveur.resoudre(entetes)));
		}
		if (entetes.getMessageType() == SimpMessageType.SUBSCRIBE && entetes.getUser() instanceof IdentiteStomp identite
				&& !AccesAuxVues.autorise(identite.acteur(), entetes.getDestination())) {
			throw new MessageDeliveryException("Destination non autorisée pour cet acteur.");
		}
		if (!prototype && entetes.getMessageType() == SimpMessageType.SUBSCRIBE && !(entetes.getUser() instanceof IdentiteStomp)) {
			throw new MessageDeliveryException("Une authentification est requise.");
		}
		return MessageBuilder.createMessage(message.getPayload(), entetes.getMessageHeaders());
	}
}
