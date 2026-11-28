package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.Acteur;
import fr.croustyquizz.manche.CommandeDeManche;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** L'Animateur fixe le verdict d'un champ d'une Proposition jugée (ADR 0009, point 15). */
public record ArbitrerProposition(Acteur acteur, Instant recueLe, UUID envoiId, UUID etapeId, UUID propositionId,
		PartieDuMorceau partie, VerdictChamp verdict) implements CommandeDeManche {

	public ArbitrerProposition {
		Objects.requireNonNull(acteur, "acteur");
		Objects.requireNonNull(recueLe, "recueLe");
		Objects.requireNonNull(envoiId, "envoiId");
		Objects.requireNonNull(etapeId, "etapeId");
		Objects.requireNonNull(propositionId, "propositionId");
		Objects.requireNonNull(partie, "partie");
		if (verdict != VerdictChamp.JUSTE && verdict != VerdictChamp.FAUX) {
			throw new IllegalArgumentException("Un arbitrage rend un champ juste ou faux.");
		}
	}

	AccuseArbitrage accuse(boolean modifie) {
		return new AccuseArbitrage(envoiId, etapeId, recueLe, propositionId, partie, verdict, modifie);
	}

}
