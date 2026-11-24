package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.ActionJoueur;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Brouillon de Proposition de titre envoyé au fil de la saisie ; il remplace le précédent (ADR 0009, point 6). */
public record DeposerBrouillon(UUID joueurId, Instant recueLe, UUID etapeId, BrouillonProposition brouillon)
		implements ActionJoueur {

	public DeposerBrouillon {
		Objects.requireNonNull(joueurId, "joueurId");
		Objects.requireNonNull(recueLe, "recueLe");
		Objects.requireNonNull(etapeId, "etapeId");
		Objects.requireNonNull(brouillon, "brouillon");
	}

}
