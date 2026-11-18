package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/** Broker Spring réel en mémoire : vérifie le câblage de la barrière sans base ni Docker. */
class ConfigurationTempsReelTest {
	@Test
	void unAbonnementDeclareInstalleRecoitVraimentLesPublicationsEtSeRetire() throws Exception {
		try (var contexte = new AnnotationConfigWebApplicationContext()) {
			contexte.setServletContext(new MockServletContext());
			contexte.register(ConfigurationTempsReel.class, RegistreAbonnements.class);
			contexte.refresh();
			var registre = contexte.getBean(RegistreAbonnements.class);
			var entrant = contexte.getBean("clientInboundChannel", MessageChannel.class);
			var sortant = contexte.getBean("clientOutboundChannel", SubscribableChannel.class);
			var publications = contexte.getBean("brokerMessagingTemplate", SimpMessagingTemplate.class);
			var messages = new LinkedBlockingQueue<Message<?>>();
			sortant.subscribe(messages::add);
			UUID abonnement = UUID.randomUUID();
			String destination = Canaux.salle(UUID.randomUUID());
			entrant.send(trame(StompCommand.CONNECT, abonnement, destination));
			assertThat(messages.poll(2, TimeUnit.SECONDS)).isNotNull();
			entrant.send(trame(StompCommand.SUBSCRIBE, abonnement, destination));
			await().atMost(Duration.ofSeconds(2)).until(() -> registre.estInstalle(abonnement, destination));
			publications.convertAndSend(destination, "Vue publiée après la barrière");
			var message = messages.poll(2, TimeUnit.SECONDS);
			assertThat(message).isNotNull();
			assertThat(new String((byte[]) message.getPayload(), StandardCharsets.UTF_8)).isEqualTo("Vue publiée après la barrière");
			entrant.send(trame(StompCommand.UNSUBSCRIBE, abonnement, destination));
			await().atMost(Duration.ofSeconds(2)).until(() -> !registre.estInstalle(abonnement, destination));
		}
	}

	private Message<byte[]> trame(StompCommand commande, UUID abonnement, String destination) {
		var entetes = StompHeaderAccessor.create(commande);
		entetes.setSessionId("connexion");
		entetes.setSubscriptionId(abonnement.toString());
		entetes.setDestination(destination);
		return MessageBuilder.createMessage(new byte[0], entetes.getMessageHeaders());
	}
}
