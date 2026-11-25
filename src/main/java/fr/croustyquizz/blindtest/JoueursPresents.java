package fr.croustyquizz.blindtest;

import java.util.Set;
import java.util.UUID;

/**
 * Port vers les Joueurs de la Soirée qui peuvent buzzer, retardataires compris. Lu sous le verrou de la Soirée, sans
 * appel externe : un Morceau se termine quand ils sont tous bloqués (ADR 0009, point 11).
 */
@FunctionalInterface
public interface JoueursPresents {

	Set<UUID> de(UUID soireeId);

}
