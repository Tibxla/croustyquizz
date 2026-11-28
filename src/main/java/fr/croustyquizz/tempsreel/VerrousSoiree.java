package fr.croustyquizz.tempsreel;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Même verrou réentrant pour mutation, transaction, publication et capture d'une Soirée. */
@Component
public final class VerrousSoiree {
	private final ReentrantLock[] verrous = new ReentrantLock[64];
	public VerrousSoiree() {
		for (int i = 0; i < verrous.length; i++) { verrous[i] = new ReentrantLock(true); }
	}
	public <T> T executer(UUID soireeId, Supplier<T> action) {
		ReentrantLock verrou = verrou(soireeId);
		verrou.lock();
		try { return action.get(); }
		finally { verrou.unlock(); }
	}
	/**
	 * Vrai si {@code fil} fait la queue devant le verrou de cette Soirée. Les tests de concurrence s'en servent :
	 * l'état {@code WAITING} d'un fil ne dit pas ce qu'il attend (pool de connexions, verrou d'un journal…).
	 */
	public boolean estAttenduPar(UUID soireeId, Thread fil) {
		return verrou(soireeId).hasQueuedThread(fil);
	}
	private ReentrantLock verrou(UUID soireeId) {
		return verrous[Math.floorMod(soireeId.hashCode(), verrous.length)];
	}
}
