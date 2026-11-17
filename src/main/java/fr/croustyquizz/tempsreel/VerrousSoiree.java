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
		ReentrantLock verrou = verrous[Math.floorMod(soireeId.hashCode(), verrous.length)];
		verrou.lock();
		try { return action.get(); }
		finally { verrou.unlock(); }
	}
}
