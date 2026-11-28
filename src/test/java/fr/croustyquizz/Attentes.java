package fr.croustyquizz;

import java.time.Duration;

/**
 * Plafonds communs des attentes asynchrones des tests. Une attente rend la main dès que sa condition est vraie :
 * un plafond large ne ralentit pas un test vert, il évite seulement les faux échecs d'une machine chargée.
 * Ne pas s'en servir pour affirmer une absence : l'attente dépenserait alors tout son plafond.
 */
public final class Attentes {
	/** Attente qui doit aboutir : message reçu, abonnement installé, fil arrivé au verrou, résultat d'une tâche. */
	public static final long PLAFOND_SECONDES = 10;
	public static final Duration PLAFOND = Duration.ofSeconds(PLAFOND_SECONDES);
	/**
	 * Fil qui retient un verrou jusqu'au feu vert du test. Le test le donne toujours dans un {@code finally} :
	 * ce plafond dépasse la suite des attentes de l'observateur, pour que le fil ne lâche pas le verrou avant lui.
	 */
	public static final long RETENUE_SECONDES = 3 * PLAFOND_SECONDES;

	private Attentes() { }
}
