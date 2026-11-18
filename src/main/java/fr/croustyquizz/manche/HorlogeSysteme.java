package fr.croustyquizz.manche;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Horloge réelle partagée. L'HorlogeDeManche replace les callbacks dans le
 * service transactionnel ; le verrou de Soirée garantit leur sérialisation.
 */
public final class HorlogeSysteme implements Horloge {

	private final Clock horloge;
	private final ScheduledExecutorService executeur;

	public HorlogeSysteme(Clock horloge, ScheduledExecutorService executeur) {
		this.horloge = horloge;
		this.executeur = executeur;
	}

	@Override
	public Instant maintenant() {
		return horloge.instant();
	}

	@Override
	public Minuteur programmer(Duration delai, Runnable action) {
		ScheduledFuture<?> tache = executeur.schedule(action, delai.toMillis(), TimeUnit.MILLISECONDS);
		return () -> tache.cancel(false);
	}

}
