package fr.croustyquizz;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

	/** L'observateur attend un signal qui doit venir, sous le {@link #PLAFOND}. */
	public static void attendre(CountDownLatch signal) {
		attendre(signal, PLAFOND_SECONDES, "Signal attendu non reçu sous le plafond.");
	}

	/** Le fil reste retenu jusqu'au feu vert du test, sous la {@link #RETENUE_SECONDES retenue}. */
	public static void retenir(CountDownLatch feuVert) {
		attendre(feuVert, RETENUE_SECONDES, "Retenue sans feu vert du test.");
	}

	/**
	 * Lève une exception non contrôlée plutôt qu'une {@link AssertionError} : un fil de serveur de test
	 * (com.sun.net.httpserver) ferme alors l'échange au lieu de relancer l'erreur.
	 */
	private static void attendre(CountDownLatch signal, long secondes, String motif) {
		try {
			if (!signal.await(secondes, TimeUnit.SECONDS)) { throw new IllegalStateException(motif); }
		} catch (InterruptedException interruption) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(motif, interruption);
		}
	}
}
