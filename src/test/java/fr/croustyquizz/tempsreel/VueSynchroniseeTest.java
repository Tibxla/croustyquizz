package fr.croustyquizz.tempsreel;

import static fr.croustyquizz.Attentes.PLAFOND;
import static fr.croustyquizz.Attentes.PLAFOND_SECONDES;
import static fr.croustyquizz.Attentes.RETENUE_SECONDES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

import fr.croustyquizz.manche.HorlogeFactice;

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
				}, new CaptureSalleFactice(), Duration.ofHours(24));
		UUID soiree = UUID.randomUUID();
		UUID abonnement = UUID.randomUUID();
		assertThat(diffuseur.capturerSalle(soiree, abonnement).revision()).isZero();
		var vue = new VueComposee("buzz-recu", java.util.Map.of("buzz", List.of(new BuzzRecu("Alpha", Instant.EPOCH))));
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
	void laPurgeSansTraficEvinceToutesLesDestinationsDeLaSoireeTerminee() {
		var horloge = new HorlogeFactice();
		var repli = new VueComposee("capture", Map.of("classement", List.of("Léa")));
		var diffuseur = new DiffuseurStomp(new SimpMessagingTemplate(new ExecutorSubscribableChannel()),
				horloge.commeClock(), new VerrousSoiree(),
				id -> repli, new CaptureSalleFactice(), Duration.ofHours(24));
		UUID terminee = UUID.randomUUID();
		UUID enCours = UUID.randomUUID();
		UUID joueur = UUID.randomUUID();
		UUID autreJoueur = UUID.randomUUID();
		var vueEnCours = new VueComposee("manche-demarree", Map.of());
		var vueFinale = new VueComposee("soiree-terminee", Map.of("classement", List.of("Léa")), horloge.maintenant());
		diffuseur.diffuserSalle(enCours, vueEnCours);
		diffuseur.envoyerJoueur(terminee, autreJoueur, vueEnCours);
		diffuseur.diffuserSalle(terminee, vueFinale);
		diffuseur.envoyerConsole(terminee, vueFinale);
		diffuseur.envoyerJoueur(terminee, joueur, vueFinale);

		horloge.avancer(Duration.ofHours(24).minusSeconds(1));
		diffuseur.evincerLesSoireesEchues();
		assertThat(diffuseur.capturerSalle(terminee, null).vue()).isEqualTo(vueFinale);
		horloge.avancer(Duration.ofSeconds(1));
		diffuseur.evincerLesSoireesEchues();
		// Un recul de l’horloge distingue la vue déjà évincée de celle qu’une capture évincerait à la demande.
		horloge.avancer(Duration.ofSeconds(-1));
		var captureReconstruite = new VueSynchronisee(0, horloge.maintenant(), null, repli);
		assertThat(diffuseur.capturerSalle(terminee, null)).isEqualTo(captureReconstruite);
		assertThat(diffuseur.capturerConsole(terminee, null)).isEqualTo(captureReconstruite);
		assertThat(diffuseur.capturerJoueur(terminee, joueur, null)).isEqualTo(captureReconstruite);
		assertThat(diffuseur.capturerJoueur(terminee, autreJoueur, null)).isEqualTo(captureReconstruite);
		assertThat(diffuseur.capturerSalle(enCours, null))
				.isEqualTo(new VueSynchronisee(1, horloge.maintenant(), null, vueEnCours));
	}

	@Test
	void unePublicationApresLeDelaiConfigureNeRecreePasLesVues() {
		var horloge = new HorlogeFactice();
		var canal = new ExecutorSubscribableChannel();
		List<Object> messages = new ArrayList<>();
		canal.subscribe(message -> messages.add(message.getPayload()));
		var repli = new VueComposee("capture", Map.of());
		var diffuseur = new DiffuseurStomp(new SimpMessagingTemplate(canal),
				horloge.commeClock(), new VerrousSoiree(),
				id -> repli, new CaptureSalleFactice(), Duration.ofHours(2));
		UUID soiree = UUID.randomUUID();
		Instant fin = horloge.maintenant();
		var vue = new VueComposee("musique-attente-modifiee", Map.of(), fin);
		diffuseur.envoyerConsole(soiree, new VueComposee("soiree-terminee", Map.of(), fin));

		horloge.avancer(Duration.ofHours(1));
		diffuseur.envoyerConsole(soiree, vue);
		assertThat(diffuseur.capturerConsole(soiree, null)).isEqualTo(new VueSynchronisee(2, horloge.maintenant(), null, vue));
		horloge.avancer(Duration.ofHours(1));
		diffuseur.evincerLesSoireesEchues();
		diffuseur.envoyerConsole(soiree, vue);

		assertThat(messages).hasSize(2);
		assertThat(diffuseur.capturerConsole(soiree, null)).isEqualTo(new VueSynchronisee(0, horloge.maintenant(), null, repli));
	}

	@Test
	void unePublicationAttendLaFinDeLaCaptureSousLeMemeVerrou() throws Exception {
		var canal = new ExecutorSubscribableChannel();
		CountDownLatch captureCommencee = new CountDownLatch(1);
		CountDownLatch terminerCapture = new CountDownLatch(1);
		var verrous = new VerrousSoiree();
		var diffuseur = new DiffuseurStomp(new SimpMessagingTemplate(canal),
				Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), verrous, soiree -> {
					captureCommencee.countDown();
					try {
						assertThat(terminerCapture.await(RETENUE_SECONDES, TimeUnit.SECONDS)).isTrue();
					} catch (InterruptedException interruption) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException(interruption);
					}
					return new VueSallePrototype(List.of());
				}, new CaptureSalleFactice(), Duration.ofHours(24));
		UUID soiree = UUID.randomUUID();
		var vue = new VueComposee("buzz-recu", java.util.Map.of("buzz", List.of(new BuzzRecu("Pendant", Instant.EPOCH))));
		AtomicReference<Thread> publication = new AtomicReference<>();
		try (var executeur = Executors.newFixedThreadPool(2)) {
			try {
				var capture = executeur.submit(() -> diffuseur.capturerSalle(soiree, null));
				assertThat(captureCommencee.await(PLAFOND_SECONDES, TimeUnit.SECONDS)).isTrue();
				var envoi = executeur.submit(() -> {
					publication.set(Thread.currentThread());
					diffuseur.diffuserSalle(soiree, vue);
				}, new CaptureSalleFactice());
				await().atMost(PLAFOND).until(() -> publication.get() != null
						&& verrous.estAttenduPar(soiree, publication.get()));
				terminerCapture.countDown();
				assertThat(capture.get(PLAFOND_SECONDES, TimeUnit.SECONDS).revision()).isZero();
				envoi.get(PLAFOND_SECONDES, TimeUnit.SECONDS);
				assertThat(diffuseur.capturerSalle(soiree, null)).isEqualTo(new VueSynchronisee(1, Instant.EPOCH, null, vue));
			} finally {
				terminerCapture.countDown();
			}
		}
	}

}
