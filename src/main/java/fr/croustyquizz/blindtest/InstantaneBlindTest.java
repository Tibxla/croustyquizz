package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.EtatManche;
import java.util.Objects;

/**
 * Capture immuable du déroulement, comparée par valeur. Elle porte la sélection et la réponse : le fournisseur de
 * bloc (#177) n'en publie que ce que chaque destination peut voir.
 */
public record InstantaneBlindTest(EtatManche etatManche, EtatBlindTestPersiste etat) {

	public InstantaneBlindTest {
		Objects.requireNonNull(etatManche, "etatManche");
		Objects.requireNonNull(etat, "etat");
	}

	/** Morceau courant, nul avant le démarrage. */
	public MorceauEnCours courant() {
		return etat.courant();
	}

	/** Dernier Buzz qui a pris la main, nul avant le premier. */
	public AccuseBuzz dernierBuzz() {
		return etat.dernierBuzz();
	}

}
