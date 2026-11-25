package fr.croustyquizz.blindtest;

import fr.croustyquizz.manche.ActionJoueur;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Buzz transmis par la route commune {@code envoyerBuzz}, qui nomme le Morceau visé (ADR 0009, point 5). */
public record BuzzerMorceau(UUID joueurId, String pseudo, Instant recueLe, UUID envoiId, UUID etapeId)
		implements ActionJoueur {

	public BuzzerMorceau {
		Objects.requireNonNull(joueurId, "joueurId");
		Objects.requireNonNull(pseudo, "pseudo");
		Objects.requireNonNull(recueLe, "recueLe");
		Objects.requireNonNull(envoiId, "envoiId");
		Objects.requireNonNull(etapeId, "etapeId");
	}

}
