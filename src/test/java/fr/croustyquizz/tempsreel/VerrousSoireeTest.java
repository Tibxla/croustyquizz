package fr.croustyquizz.tempsreel;

import static fr.croustyquizz.Attentes.PLAFOND_SECONDES;
import static fr.croustyquizz.Attentes.RETENUE_SECONDES;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
	private static void attendre(CountDownLatch verrou) {
		try { assertThat(verrou.await(RETENUE_SECONDES, TimeUnit.SECONDS)).isTrue(); }
		catch (InterruptedException interruption) { Thread.currentThread().interrupt(); throw new IllegalStateException(interruption); }
	}
}
