package fr.croustyquizz.quiz;

import fr.croustyquizz.manche.ActionJoueur;
import fr.croustyquizz.manche.Acteur;
import java.time.Instant;
import java.util.UUID;

public record EnvoyerReponse(
		Acteur acteur,
		Instant recueLe,
		UUID envoiId,
		UUID etapeId,
		UUID propositionId
) implements ActionJoueur {
	@Override
	public UUID joueurId() {
		return acteur.identifiant();
	}
}
