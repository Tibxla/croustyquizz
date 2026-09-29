package fr.croustyquizz.manche;

import java.util.UUID;

import fr.croustyquizz.tempsreel.DiffuseurTempsReel;

/**
 * Tout ce qu'un déroulement reçoit du socle à sa création.
 *
 * @param soireeId    Soirée dans laquelle la Manche se joue
 * @param mancheId    Manche à dérouler
 * @param reglages    réglages communs choisis par l'Animateur
 * @param horloge     seule source de temps et de minuteurs
 * @param diffuseur   seule sortie vers les écrans
 * @param signalerFin à appeler une fois, quand la Manche se termine d'elle-même
 */
public record ContexteDeManche(
		UUID soireeId,
		UUID mancheId,
		ReglagesDeManche reglages,
		Horloge horloge,
		DiffuseurTempsReel diffuseur,
		Runnable signalerFin) {
}
