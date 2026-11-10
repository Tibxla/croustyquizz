package fr.croustyquizz.tempsreel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Les pages se connectent en WebSocket sur {@value #POINT_DE_CONNEXION}, s'abonnent
 * aux destinations de {@link Canaux}. Les actions et consultations passent par HTTP.
 */
@Configuration
@EnableWebSocketMessageBroker
class ConfigurationTempsReel implements WebSocketMessageBrokerConfigurer {

	static final String POINT_DE_CONNEXION = "/ws";
	static final String PREFIXE_ACTIONS = "/app";
	private final RegistreAbonnements abonnements;
	private TaskScheduler ordonnanceur;

	ConfigurationTempsReel(RegistreAbonnements abonnements) {
		this.abonnements = abonnements;
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration canal) {
		canal.interceptors(new ChannelInterceptor() {
			@Override
			public Message<?> preSend(Message<?> message, MessageChannel destination) {
				if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.MESSAGE) {
					throw new MessageDeliveryException("Les commandes passent par HTTP");
				}
				return message;
			}
		});
		canal.interceptors(abonnements);
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registre) {
		registre.addEndpoint(POINT_DE_CONNEXION);
		// Préserve l'ordre des opérations techniques d'une même connexion.
		registre.setPreserveReceiveOrder(true);
	}

	@Autowired
	void definirOrdonnanceur(@Lazy TaskScheduler messageBrokerTaskScheduler) {
		this.ordonnanceur = messageBrokerTaskScheduler;
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registre) {
		registre.enableSimpleBroker("/topic").setTaskScheduler(ordonnanceur).setHeartbeatValue(new long[] { 10000, 10000 });
		registre.setApplicationDestinationPrefixes(PREFIXE_ACTIONS);
		// Chaque page reçoit les messages dans l'ordre où le serveur les publie
		// (l'ordre des Buzz compte).
		registre.setPreservePublishOrder(true);
	}

}
