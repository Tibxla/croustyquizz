package fr.croustyquizz.tempsreel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

class VueSynchroniseeTest {

	@Test
	void laCaptureRendLaDerniereVueEtLesRevisionsRestentPropresALaDestination() {
		var canal = new ExecutorSubscribableChannel();
		List<String> messages = new ArrayList<>();
		canal.subscribe(message -> messages.add(message.getPayload().toString()));
		AtomicInteger replis = new AtomicInteger();
		var diffuseur = new DiffuseurStomp(new SimpMessagingTemplate(canal),
				Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), new VerrousSoiree(), soiree -> {
					replis.incrementAndGet();
					return new VueSallePrototype(List.of());
				});
		UUID soiree = UUID.randomUUID();
		UUID abonnement = UUID.randomUUID();
		assertThat(diffuseur.capturerSalle(soiree, abonnement).revision()).isZero();
		var vue = new VueSallePrototype(List.of(new BuzzRecu("Alpha", Instant.EPOCH)));
		diffuseur.diffuserSalle(soiree, vue);
		diffuseur.envoyerConsole(soiree, vue);
		diffuseur.diffuserSalle(soiree, vue);
		var capture = diffuseur.capturerSalle(soiree, abonnement);
		assertThat(capture).isEqualTo(new VueSynchronisee(2, Instant.EPOCH, abonnement, vue));
		assertThat(diffuseur.capturerSalle(UUID.randomUUID(), null).revision()).isZero();
		assertThat(replis).hasValue(2);
		assertThat(messages).hasSize(3);
		assertThat(messages.get(0)).contains("revision=1", "soireeId=" + soiree);
		assertThat(messages.get(1)).contains("revision=1");
		assertThat(messages.get(2)).contains("revision=2");
	}
	@Test
	void unePublicationAttendLaFinDeLaCaptureSousLeMemeVerrou() throws Exception {
		var canal = new ExecutorSubscribableChannel();
		CountDownLatch captureCommencee = new CountDownLatch(1);
		CountDownLatch terminerCapture = new CountDownLatch(1);
		var diffuseur = new DiffuseurStomp(new SimpMessagingTemplate(canal),
				Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), new VerrousSoiree(), soiree -> {
					captureCommencee.countDown();
					try {
						assertThat(terminerCapture.await(5, TimeUnit.SECONDS)).isTrue();
					} catch (InterruptedException interruption) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException(interruption);
					}
					return new VueSallePrototype(List.of());
				});
		UUID soiree = UUID.randomUUID();
		var vue = new VueSallePrototype(List.of(new BuzzRecu("Pendant", Instant.EPOCH)));
		AtomicReference<Thread> publication = new AtomicReference<>();
		try (var executeur = Executors.newFixedThreadPool(2)) {
			try {
				var capture = executeur.submit(() -> diffuseur.capturerSalle(soiree, null));
				assertThat(captureCommencee.await(2, TimeUnit.SECONDS)).isTrue();
				var envoi = executeur.submit(() -> {
					publication.set(Thread.currentThread());
					diffuseur.diffuserSalle(soiree, vue);
				});
				await().atMost(Duration.ofSeconds(2)).until(() -> publication.get() != null
						&& publication.get().getState() == Thread.State.BLOCKED);
				terminerCapture.countDown();
				assertThat(capture.get(2, TimeUnit.SECONDS).revision()).isZero();
				envoi.get(2, TimeUnit.SECONDS);
				assertThat(diffuseur.capturerSalle(soiree, null)).isEqualTo(new VueSynchronisee(1, Instant.EPOCH, null, vue));
			} finally {
				terminerCapture.countDown();
			}
		}
	}

}
