package fr.croustyquizz.tempsreel;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Les pages se connectent en WebSocket sur {@value #POINT_DE_CONNEXION}, s'abonnent
 * aux destinations de {@link Canaux} et envoient leurs actions sous {@value #PREFIXE_ACTIONS}.
 */
@Configuration
@EnableWebSocketMessageBroker
class ConfigurationTempsReel implements WebSocketMessageBrokerConfigurer {

	static final String POINT_DE_CONNEXION = "/ws";
	static final String PREFIXE_ACTIONS = "/app";

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registre) {
		registre.addEndpoint(POINT_DE_CONNEXION);
		// Les messages d'une même page sont traités dans l'ordre où elle les envoie :
		// un abonnement est enregistré avant l'action qui le suit.
		registre.setPreserveReceiveOrder(true);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registre) {
		registre.enableSimpleBroker("/topic");
		registre.setApplicationDestinationPrefixes(PREFIXE_ACTIONS);
		// Chaque page reçoit les messages dans l'ordre où le serveur les publie
		// (l'ordre des Buzz compte).
		registre.setPreservePublishOrder(true);
	}

}
