package fr.croustyquizz.manche;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Horloge de test : le temps n'avance que par {@link #avancer(Duration)}, et les
 * minuteurs échus s'exécutent à ce moment-là, dans l'ordre de leurs échéances.
 */
public final class HorlogeFactice implements Horloge {

	private final PriorityQueue<Programme> programmes = new PriorityQueue<>(
			Comparator.comparing(Programme::echeance).thenComparingLong(Programme::ordre));
	private Instant maintenant;
	private long compteur;

	public HorlogeFactice() {
		this(Instant.parse("2026-10-02T20:00:00Z"));
	}

	public HorlogeFactice(Instant depart) {
		this.maintenant = depart;
	}

	@Override
	public Instant maintenant() {
		return maintenant;
	}

	@Override
	public Minuteur programmer(Duration delai, Runnable action) {
		Programme programme = new Programme(maintenant.plus(delai), compteur++, action);
		programmes.add(programme);
		return () -> programmes.remove(programme);
	}

	/** Avance le temps et exécute les minuteurs échus, y compris ceux programmés en chemin. */
	public void avancer(Duration duree) {
		Instant cible = maintenant.plus(duree);
		while (!programmes.isEmpty() && !programmes.peek().echeance().isAfter(cible)) {
			Programme programme = programmes.poll();
			maintenant = programme.echeance();
			programme.action().run();
		}
		maintenant = cible;
	}

	public int minuteursEnAttente() {
		return programmes.size();
	}

	private record Programme(Instant echeance, long ordre, Runnable action) {
	}

}
