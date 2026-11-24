package fr.croustyquizz.tempsreel;

import java.util.UUID;
import java.util.regex.Pattern;

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

import fr.croustyquizz.manche.EtatDeSoiree;

/** Identifie CONNECT et réserve l'abonnement anonyme aux canaux de salle de démonstration. */
@Component
public final class ControleAccesStomp implements ChannelInterceptor {
	private static final Pattern CANAL_SALLE_PROTOTYPE = Pattern.compile(
			"/topic/soirees/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/salle");
	private final ResolveurDActeur resolveur;
	private final EtatDeSoiree soirees;
	private final boolean prototype;
	public ControleAccesStomp(ResolveurDActeur resolveur, Environment environnement, EtatDeSoiree soirees) {
		this.resolveur = resolveur;
		this.soirees = soirees;
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
		if (entetes.getMessageType() == SimpMessageType.SUBSCRIBE && !(entetes.getUser() instanceof IdentiteStomp)) {
			if (!autoriseSansIdentite(entetes.getDestination())) {
				throw new MessageDeliveryException("Une authentification est requise.");
			}
		}
		return MessageBuilder.createMessage(message.getPayload(), entetes.getMessageHeaders());
	}

	private boolean autoriseSansIdentite(String destination) {
		if (!prototype || destination == null) { return false; }
		var canal = CANAL_SALLE_PROTOTYPE.matcher(destination);
		return canal.matches() && !soirees.estEnregistree(UUID.fromString(canal.group(1)));
	}
}
