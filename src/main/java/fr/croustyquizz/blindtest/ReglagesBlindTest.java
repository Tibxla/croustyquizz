package fr.croustyquizz.blindtest;

import java.util.Objects;

/**
 * Réglages publics d'une Manche de Blind test, publiés à toutes les destinations : la sélection tirée n'y figure
 * jamais, elle reste dans {@link EtatBlindTestPersiste} (ADR 0009, point 1).
 */
public record ReglagesBlindTest(SourceDeMorceaux source, int nombreMorceaux) {

	public static final int NOMBRE_MAX_MORCEAUX = 50;

	public ReglagesBlindTest {
		Objects.requireNonNull(source, "source");
		if (nombreMorceaux < 1 || nombreMorceaux > NOMBRE_MAX_MORCEAUX) {
			throw new IllegalArgumentException("Une Manche de Blind test compte de 1 à 50 Morceaux.");
		}
	}

}
