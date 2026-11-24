package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.ActionJoueur;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Brouillon de Proposition de titre envoyé au fil de la saisie ; il remplace le brouillon retenu si son numéro
 * d'envoi est plus grand (ADR 0009, point 6).
 *
 * @param numero numéro d'envoi croissant pendant la main, à partir de 1
 */
public record DeposerBrouillon(UUID joueurId, Instant recueLe, UUID etapeId, long numero,
		BrouillonProposition brouillon) implements ActionJoueur {

	public DeposerBrouillon {
		Objects.requireNonNull(joueurId, "joueurId");
		Objects.requireNonNull(recueLe, "recueLe");
		Objects.requireNonNull(etapeId, "etapeId");
		Objects.requireNonNull(brouillon, "brouillon");
		if (numero <= 0) {
			throw new IllegalArgumentException("Un brouillon est numéroté à partir de 1.");
		}
	}

}
