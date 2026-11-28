package fr.croustyquizz.manche;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Horloge de test : le temps n'avance que par {@link #avancer(Duration)}, et les
 * minuteurs échus s'exécutent à ce moment-là, dans l'ordre de leurs échéances.
 * Sûre entre fils : une tâche de fond peut programmer pendant que le test avance.
 * Le moniteur n'est jamais tenu pendant une action, qui peut attendre le verrou
 * d'une Soirée tenu par un fil qui programme.
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
	public synchronized Instant maintenant() {
		return maintenant;
	}

	public Clock commeClock() {
		return ((InstantSource) this::maintenant).withZone(ZoneOffset.UTC);
	}

	@Override
	public synchronized Minuteur programmer(Duration delai, Runnable action) {
		Programme programme = new Programme(maintenant.plus(delai), compteur++, action);
		programmes.add(programme);
		return () -> annuler(programme);
	}

	private synchronized void annuler(Programme programme) {
		programmes.remove(programme);
	}

	/** Avance le temps et exécute les minuteurs échus, y compris ceux programmés en chemin. */
	public void avancer(Duration duree) {
		Instant cible;
		synchronized (this) {
			cible = maintenant.plus(duree);
		}
		while (true) {
			Programme programme;
			synchronized (this) {
				if (programmes.isEmpty() || programmes.peek().echeance().isAfter(cible)) {
					maintenant = cible;
					return;
				}
				programme = programmes.poll();
				maintenant = programme.echeance();
			}
			programme.action().run();
		}
	}

	public synchronized int minuteursEnAttente() {
		return programmes.size();
	}

	private record Programme(Instant echeance, long ordre, Runnable action) {
	}

}
