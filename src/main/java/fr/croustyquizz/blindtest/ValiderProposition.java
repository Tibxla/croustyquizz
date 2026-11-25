package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.ActionJoueur;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Validation d'une Proposition de titre, jugée aussitôt (ADR 0009, point 6). */
public record ValiderProposition(UUID joueurId, Instant recueLe, UUID envoiId, UUID etapeId,
		BrouillonProposition proposition) implements ActionJoueur {

	public ValiderProposition {
		Objects.requireNonNull(joueurId, "joueurId");
		Objects.requireNonNull(recueLe, "recueLe");
		Objects.requireNonNull(envoiId, "envoiId");
		Objects.requireNonNull(etapeId, "etapeId");
		Objects.requireNonNull(proposition, "proposition");
	}

}
