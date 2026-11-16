package fr.croustyquizz.tempsreel;

import java.util.UUID;

/** Repli propre au Buzz de test, indépendant de l'assemblage des vues métier. */
@FunctionalInterface
public interface CaptureVuePrototype {
	Evenement capturerSalle(UUID soireeId);
}
