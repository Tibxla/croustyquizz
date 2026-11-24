package fr.croustyquizz.tempsreel;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Pattern;

import fr.croustyquizz.animateur.AnimateurConnecte;
import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.RefusDeCommande;
import org.springframework.http.HttpHeaders;
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
	static final String ACTEUR_ABONNE = "croustyquizz.acteurAbonne";
	private static final Pattern CANAL_SOIREE = Pattern.compile(
			"/topic/soirees/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/(salle|console)");
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
		if (entetes.getMessageType() == SimpMessageType.CONNECT) {
			identifier(entetes);
		}
		if (entetes.getMessageType() == SimpMessageType.SUBSCRIBE) {
			try {
				autoriserAbonnement(entetes);
			} catch (RefusDeCommande refus) {
				PoigneeDeMainAnimateur.refuser(entetes, refus.getMessage());
				return null;
			}
		}
		return MessageBuilder.createMessage(message.getPayload(), entetes.getMessageHeaders());
	}

	private void identifier(SimpMessageHeaderAccessor entetes) {
		if (entetes.getFirstNativeHeader(HttpHeaders.AUTHORIZATION) == null) {
			var animateur = PoigneeDeMainAnimateur.connecter(entetes);
			if (animateur != null) {
				entetes.setUser(new IdentiteAnimateur(animateur));
				return;
			}
			if (prototype) {
				return;
			}
		}
		entetes.setUser(new IdentiteStomp(resolveur.resoudre(entetes)));
	}

	private void autoriserAbonnement(SimpMessageHeaderAccessor entetes) {
		String destination = entetes.getDestination();
		if (entetes.getUser() instanceof IdentiteStomp identite) {
			verifierDestination(entetes, destination, identite.acteur());
			return;
		}
		var canal = CANAL_SOIREE.matcher(destination == null ? "" : destination);
		boolean canalDeSoiree = canal.matches();
		if (entetes.getUser() instanceof IdentiteAnimateur identite) {
			if (!canalDeSoiree) {
				throw new MessageDeliveryException("Destination non autorisée pour cet acteur.");
			}
			Acteur acteur = resolveur.resoudre(identite.animateur(), UUID.fromString(canal.group(1)));
			verifierDestination(entetes, destination, acteur);
			return;
		}
		if (!prototype || !canalDeSoiree || !"salle".equals(canal.group(2))) {
			throw new MessageDeliveryException("Une authentification est requise.");
		}
	}

	private static void verifierDestination(SimpMessageHeaderAccessor entetes, String destination, Acteur acteur) {
		if (!AccesAuxVues.autorise(acteur, destination)) {
			throw new MessageDeliveryException("Destination non autorisée pour cet acteur.");
		}
		entetes.setHeader(ACTEUR_ABONNE, acteur);
	}

	private record IdentiteAnimateur(AnimateurConnecte animateur) implements Principal {
		@Override
		public String getName() {
			return animateur.animateurId().toString();
		}
	}
}
