package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;

class EntreesHttpTest {

	@Test
	void unSecondBuzzNePeutEtreHorodateAvantLaPublicationDuPremier() throws Exception {
		Clock horloge = mock(Clock.class);
		AtomicInteger lectures = new AtomicInteger();
		when(horloge.instant()).thenAnswer(invocation -> Instant.EPOCH.plusSeconds(lectures.incrementAndGet()));
		DiffuseurTempsReel diffuseur = mock(DiffuseurTempsReel.class);
		UUID soiree = UUID.randomUUID();
		CountDownLatch publicationCommencee = new CountDownLatch(1);
		CountDownLatch autoriserPublication = new CountDownLatch(1);
		doAnswer(invocation -> {
			if (((VueSallePrototype) invocation.getArgument(1)).buzz().getLast().pseudo().equals("Premier")) {
				publicationCommencee.countDown();
				assertThat(autoriserPublication.await(5, TimeUnit.SECONDS)).isTrue();
			}
			return null;
		}).when(diffuseur).diffuserSalle(eq(soiree), any());
		BuzzControleur controleur = new BuzzControleur(diffuseur, horloge, new VerrousSoiree());
		AtomicReference<Thread> secondThread = new AtomicReference<>();
		try (var executeur = Executors.newFixedThreadPool(2)) {
			try {
				var premier = executeur.submit(() -> controleur.buzzer(soiree, new BuzzControleur.DemandeDeBuzz("Premier")));
				assertThat(publicationCommencee.await(2, TimeUnit.SECONDS)).isTrue();
				var second = executeur.submit(() -> {
					secondThread.set(Thread.currentThread());
					return controleur.buzzer(soiree, new BuzzControleur.DemandeDeBuzz("Second"));
				});
				await().atMost(Duration.ofSeconds(2)).until(() -> secondThread.get() != null
						&& secondThread.get().getState() == Thread.State.BLOCKED);
				assertThat(lectures.get()).isEqualTo(1);
				autoriserPublication.countDown();
				assertThat(premier.get(2, TimeUnit.SECONDS).recuLe()).isBefore(second.get(2, TimeUnit.SECONDS).recuLe());
			} finally {
				autoriserPublication.countDown();
			}
		}
	}

	@Test
	void lesClientsNePeuventEnvoyerNiCommandeNiEvenementSurStomp() {
		CanalDeTest canal = new CanalDeTest();
		new ConfigurationTempsReel(new RegistreAbonnements()).configureClientInboundChannel(canal);
		ChannelInterceptor filtre = canal.filtre();
		for (String destination : new String[] { "/app/soirees/test/buzz", "/topic/soirees/test/salle" }) {
			var entetes = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
			entetes.setDestination(destination);
			var message = MessageBuilder.createMessage(new byte[0], entetes.getMessageHeaders());
			assertThatThrownBy(() -> filtre.preSend(message, mock(org.springframework.messaging.MessageChannel.class)))
					.isInstanceOf(MessageDeliveryException.class);
		}
		var entetes = SimpMessageHeaderAccessor.create(SimpMessageType.SUBSCRIBE);
		var abonnement = MessageBuilder.createMessage(new byte[0], entetes.getMessageHeaders());
		assertThat(filtre.preSend(abonnement, mock(org.springframework.messaging.MessageChannel.class))).isSameAs(abonnement);
	}

	private static class CanalDeTest extends ChannelRegistration {
		ChannelInterceptor filtre() {
			return getInterceptors().getFirst();
		}
	}
}
