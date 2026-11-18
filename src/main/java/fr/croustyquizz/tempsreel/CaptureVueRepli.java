package fr.croustyquizz.tempsreel;

import java.util.UUID;

/** Repli sous le verrou de la Soirée, uniquement avant la première publication. */
@FunctionalInterface
public interface CaptureVueRepli {
	Evenement capturerSalle(UUID soireeId);
	default Evenement capturerConsole(UUID soireeId) { return capturerSalle(soireeId); }
	default Evenement capturerJoueur(UUID soireeId, UUID joueurId) { return capturerSalle(soireeId); }
}
