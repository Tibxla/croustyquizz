package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/** Un CONNECT refusé reçoit une trame ERROR lisible ; les autres refus restent des exceptions (#241). */
class RefusDeConnexionStompTest {

	@Test
	void leBrokerPublieUneTrameErrorAvecLeMotifDuRefusPourLaSessionRefusee() throws Exception {
		try (var contexte = new AnnotationConfigWebApplicationContext()) {
			contexte.setServletContext(new MockServletContext());
			contexte.getEnvironment().setActiveProfiles("recette");
			contexte.register(ConfigurationTempsReel.class, RegistreAbonnements.class,
					ControleAccesStomp.class, ResolveurDActeurDemonstration.class, EtatDeSoireeDemonstration.class);
			contexte.refresh();
			var sortant = contexte.getBean("clientOutboundChannel", SubscribableChannel.class);
			var messages = new LinkedBlockingQueue<Message<?>>();
			sortant.subscribe(messages::add);

			var connect = StompHeaderAccessor.create(StompCommand.CONNECT);
			connect.setSessionId("connexion-refusee");
			connect.setNativeHeader("Authorization", "Bearer demo:INCONNU:" + UUID.randomUUID() + ":" + UUID.randomUUID());

			boolean envoye = contexte.getBean("clientInboundChannel", MessageChannel.class)
					.send(MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders()));

			assertThat(envoye).as("le CONNECT refusé est retiré sans exception").isFalse();
			var erreur = StompHeaderAccessor.wrap(messages.poll(2, TimeUnit.SECONDS));
			assertThat(erreur.getCommand()).isEqualTo(StompCommand.ERROR);
			assertThat(erreur.getSessionId()).isEqualTo("connexion-refusee");
			assertThat(erreur.getMessage()).isEqualTo("Le jeton de démonstration est invalide.");
			assertThat(messages.poll(200, TimeUnit.MILLISECONDS)).as("aucun CONNECTED").isNull();
		}
	}

	@Test
	void unePanneTechniqueDonneUnMotifGeneriqueSansDetailInterne() {
		var publies = new ArrayList<Message<?>>();
		var garde = new RefusDeConnexionStomp(echoue(new IllegalStateException("Invalid CSRF Token")), canal(publies));

		assertThat(garde.preSend(trame(StompCommand.CONNECT, "connexion"), null)).isNull();

		assertThat(StompHeaderAccessor.wrap(publies.getFirst()).getMessage()).isEqualTo(RefusDeConnexionStomp.MOTIF_PAR_DEFAUT);
	}

	@Test
	void unAbonnementRefuseResteUneExceptionSansTrameError() {
		var publies = new ArrayList<Message<?>>();
		var garde = new RefusDeConnexionStomp(echoue(new MessageDeliveryException("Destination non autorisée pour cet acteur.")),
				canal(publies));

		assertThatThrownBy(() -> garde.preSend(trame(StompCommand.SUBSCRIBE, "connexion"), null))
				.isInstanceOf(MessageDeliveryException.class);
		assertThat(publies).isEmpty();
	}

	@Test
	void unConnectAccepteTraverseLeGardeSansTrameError() {
		var publies = new ArrayList<Message<?>>();
		var garde = new RefusDeConnexionStomp(new ChannelInterceptor() { }, canal(publies));
		var connect = trame(StompCommand.CONNECT, "connexion");

		assertThat(garde.preSend(connect, null)).isSameAs(connect);
		assertThat(publies).isEmpty();
	}

	private static ChannelInterceptor echoue(RuntimeException panne) {
		return new ChannelInterceptor() {
			@Override
			public Message<?> preSend(Message<?> message, MessageChannel canal) {
				throw panne;
			}
		};
	}

	private static MessageChannel canal(List<Message<?>> publies) {
		return (message, delai) -> publies.add(message);
	}

	private static Message<byte[]> trame(StompCommand commande, String session) {
		var entetes = StompHeaderAccessor.create(commande);
		entetes.setSessionId(session);
		if (commande == StompCommand.SUBSCRIBE) {
			entetes.setSubscriptionId(UUID.randomUUID().toString());
			entetes.setDestination(Canaux.salle(UUID.randomUUID()));
		}
		return MessageBuilder.createMessage(new byte[0], entetes.getMessageHeaders());
	}
}
