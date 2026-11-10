package fr.croustyquizz.tempsreel;

import java.util.UUID;

import org.springframework.stereotype.Component;

/** Même verrou pour mutation, publication et capture d'une Soirée. */
@Component
final class VerrousSoiree {
	private final Object[] verrous = new Object[64];

	VerrousSoiree() {
		for (int i = 0; i < verrous.length; i++) {
			verrous[i] = new Object();
		}
	}

	Object pour(UUID soireeId) {
		// Deux Soirées partageant une case sont simplement sérialisées ensemble.
		return verrous[Math.floorMod(soireeId.hashCode(), verrous.length)];
	}
}
