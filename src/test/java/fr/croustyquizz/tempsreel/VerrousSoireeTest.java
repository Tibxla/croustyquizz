package fr.croustyquizz.tempsreel;

import static fr.croustyquizz.Attentes.PLAFOND;
import static fr.croustyquizz.Attentes.PLAFOND_SECONDES;
import static fr.croustyquizz.Attentes.RETENUE_SECONDES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Une Soirée lente n'arrête pas les autres cases de verrou. */
class VerrousSoireeTest {
	@Test
	void deuxSoireesSansCollisionNeSeBloquentPas() throws Exception {
		var verrous = new VerrousSoiree();
		UUID premiere = new UUID(0, 1); UUID seconde = new UUID(0, 2);
		var entree = new CountDownLatch(1); var sortir = new CountDownLatch(1);
		try (var fils = Executors.newFixedThreadPool(2)) {
			try {
				var lente = fils.submit(() -> verrous.executer(premiere, () -> {
					entree.countDown(); attendre(sortir); return "première";
				}));
				assertThat(entree.await(PLAFOND_SECONDES, TimeUnit.SECONDS)).isTrue();
				assertThat(fils.submit(() -> verrous.executer(seconde, () -> "seconde")).get(PLAFOND_SECONDES, TimeUnit.SECONDS)).isEqualTo("seconde");
				assertThat(lente.isDone()).isFalse();
				sortir.countDown(); assertThat(lente.get(PLAFOND_SECONDES, TimeUnit.SECONDS)).isEqualTo("première");
			} finally { sortir.countDown(); }
		}
	}

	@Test
	void seulUnFilEnAttenteDuVerrouDeLaSoireeEstVuCommeAttendant() throws Exception {
		var verrous = new VerrousSoiree();
		UUID soiree = new UUID(0, 1); UUID autre = new UUID(0, 2);
		var sortir = new CountDownLatch(1);
		var autreFil = new AtomicReference<Thread>(); var filEnAttente = new AtomicReference<Thread>();
		try (var fils = Executors.newFixedThreadPool(2)) {
			try {
				// Un fil bloqué ailleurs que sur le verrou (ici un loquet) n'attend pas la Soirée.
				var ailleurs = fils.submit(() -> { autreFil.set(Thread.currentThread()); attendre(sortir); });
				await().atMost(PLAFOND).until(() -> autreFil.get() != null && autreFil.get().getState() == Thread.State.TIMED_WAITING);
				assertThat(verrous.estAttenduPar(soiree, autreFil.get())).isFalse();

				verrous.executer(soiree, () -> {
					var attente = fils.submit(() -> {
						filEnAttente.set(Thread.currentThread());
						return verrous.executer(soiree, () -> "après");
					});
					await().atMost(PLAFOND).until(() -> filEnAttente.get() != null && verrous.estAttenduPar(soiree, filEnAttente.get()));
					assertThat(verrous.estAttenduPar(autre, filEnAttente.get())).isFalse();
					assertThat(attente.isDone()).isFalse();
					return null;
				});
				await().atMost(PLAFOND).until(() -> !verrous.estAttenduPar(soiree, filEnAttente.get()));
				sortir.countDown(); ailleurs.get(PLAFOND_SECONDES, TimeUnit.SECONDS);
			} finally { sortir.countDown(); }
		}
	}

	private static void attendre(CountDownLatch verrou) {
		try { assertThat(verrou.await(RETENUE_SECONDES, TimeUnit.SECONDS)).isTrue(); }
		catch (InterruptedException interruption) { Thread.currentThread().interrupt(); throw new IllegalStateException(interruption); }
	}
}
