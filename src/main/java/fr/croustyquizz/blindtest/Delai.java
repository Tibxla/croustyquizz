package fr.croustyquizz.blindtest;

import java.time.Duration;
import java.time.Instant;

/**
 * Délai compté par l'Horloge du serveur : il court jusqu'à {@code echeanceLe}, ou il est gelé par une pause de la
 * Manche avec {@code restantMs} millisecondes, qui repartent telles quelles à la reprise (ADR 0009, point 6).
 */
public record Delai(Instant echeanceLe, Long restantMs) {

	public Delai {
		if ((echeanceLe == null) == (restantMs == null)) {
			throw new IllegalArgumentException("Un délai court jusqu'à une échéance, ou il est gelé.");
		}
		if (restantMs != null && restantMs < 0) {
			throw new IllegalArgumentException("Un délai gelé ne peut pas être négatif.");
		}
	}

	static Delai depuis(Instant maintenant, Duration duree) {
		return new Delai(maintenant.plus(duree), null);
	}

	static Delai gele(Duration reste) {
		return new Delai(null, reste.toMillis());
	}

	boolean estGele() {
		return echeanceLe == null;
	}

	/** Temps restant, jamais négatif. */
	Duration restantA(Instant maintenant) {
		if (estGele()) {
			return Duration.ofMillis(restantMs);
		}
		Duration reste = Duration.between(maintenant, echeanceLe);
		return reste.isNegative() ? Duration.ZERO : reste;
	}

	Delai geleA(Instant maintenant) {
		return estGele() ? this : gele(restantA(maintenant));
	}

	Delai relanceA(Instant maintenant) {
		return estGele() ? depuis(maintenant, restantA(maintenant)) : this;
	}

}
