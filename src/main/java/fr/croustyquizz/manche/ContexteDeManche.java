package fr.croustyquizz.manche;

import java.util.UUID;


/**
 * Tout ce qu'un déroulement reçoit du socle à sa création ou à sa restauration.
 *
 * @param soireeId    Soirée dans laquelle la Manche se joue
 * @param mancheId    Manche à dérouler
 * @param reglages    réglages communs choisis par l'Animateur
 * @param horloge     seule source de temps et de minuteurs
 * @param signalerFin à appeler une fois, quand la Manche se termine d'elle-même
 * @param pilotageAutomatique vrai si la Soirée est en Pilotage Automatique à la construction
 */
public record ContexteDeManche(
		UUID soireeId,
		UUID mancheId,
		ReglagesDeManche reglages,
		Horloge horloge,
		Runnable signalerFin,
		boolean pilotageAutomatique) {
	public ContexteDeManche(UUID soireeId, UUID mancheId, ReglagesDeManche reglages, Horloge horloge, Runnable signalerFin) {
		this(soireeId, mancheId, reglages, horloge, signalerFin, false);
	}
}
