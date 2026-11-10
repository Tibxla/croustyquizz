package fr.croustyquizz.tempsreel;

import java.util.UUID;

/** Repli sous le verrou de la Soirée, uniquement avant la première publication. */
@FunctionalInterface
public interface CaptureVueRepli {
	Evenement capturerSalle(UUID soireeId);
}
