package fr.croustyquizz.blindtest;

import java.util.Objects;
import java.util.UUID;

/** Dernier verdict renversé par l'Animateur sur le Morceau, annoncé brièvement à la salle (contrat : ArbitrageMorceau). */
public record ArbitrageMorceau(PartieDuMorceau partie, UUID joueurId, String pseudo, VerdictChamp verdict) {

	public ArbitrageMorceau {
		Objects.requireNonNull(partie, "partie");
		Objects.requireNonNull(joueurId, "joueurId");
		Objects.requireNonNull(pseudo, "pseudo");
		if (verdict != VerdictChamp.JUSTE && verdict != VerdictChamp.FAUX) {
			throw new IllegalArgumentException("Un arbitrage rend un champ juste ou faux.");
		}
	}

}
