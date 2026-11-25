package fr.croustyquizz.quiz;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReponseSaisie(
		@NotNull UUID envoiId,
		@NotNull UUID propositionId
) {
}
