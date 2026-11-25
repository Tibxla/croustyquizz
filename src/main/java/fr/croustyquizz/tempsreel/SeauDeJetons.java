package fr.croustyquizz.tempsreel;

import java.time.Duration;

/**
 * Seau à jetons d'une clé, tenu par son heure théorique de remplissage (algorithme GCRA) : un seul nombre suffit.
 * Le seau contient {@code rafale} jetons et en regagne un à chaque {@code intervalle} ; il est plein quand cette
 * heure est passée. Le temps arrive en paramètre, en nanosecondes, depuis l'Horloge du filtre : rien ici ne lit
 * l'heure du système. Non synchronisé : le filtre ne le manipule que dans un {@code compute} de sa table.
 */
final class SeauDeJetons {

	private final long intervalle;
	private final long tolerance;
	private long pleinA;
	private boolean refusSignale;

	SeauDeJetons(int rafale, Duration intervalle, long maintenant) {
		this.intervalle = intervalle.toNanos();
		this.tolerance = (rafale - 1) * this.intervalle;
		this.pleinA = maintenant;
	}

	/** Prend un jeton : rend 0 s'il y en avait un, sinon l'attente en nanosecondes avant le prochain, sans rien consommer. */
	long prendre(long maintenant) {
		long depart = Math.max(pleinA, maintenant);
		long attente = depart - maintenant - tolerance;
		if (attente > 0) {
			return attente;
		}
		pleinA = depart + intervalle;
		refusSignale = false;
		return 0;
	}

	/** Vrai au premier refus qui suit une acceptation : le journal ne note qu'un refus par série. */
	boolean premierRefus() {
		boolean premier = !refusSignale;
		refusSignale = true;
		return premier;
	}

	/** Attente avant le prochain jeton, sans rien prendre : 0 s'il en reste un. */
	long attente(long maintenant) {
		return Math.max(0, Math.max(pleinA, maintenant) - maintenant - tolerance);
	}

	/**
	 * Débite un jeton après coup, même si le seau est vide : il s'endette, et sa prochaine attente compte cette
	 * dette. Des requêtes simultanées passent donc toutes, mais le rythme moyen reste celui du seuil.
	 */
	void debiter(long maintenant) {
		pleinA = Math.max(pleinA, maintenant) + intervalle;
	}

	/** Rend le dernier jeton pris, quand une autre limite refuse la même requête. */
	void rendre() {
		pleinA -= intervalle;
	}

	/** Un seau plein se comporte comme un seau neuf : le filtre peut l'oublier sans rien perdre. */
	boolean estPlein(long maintenant) {
		return pleinA <= maintenant;
	}

}
