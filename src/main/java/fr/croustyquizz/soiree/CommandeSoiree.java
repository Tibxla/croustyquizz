package fr.croustyquizz.soiree;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;

/** Corps de piloterSoiree, commun à la route et au pilotage interne. */
record CommandeSoiree(@NotNull Action action) {
	enum Action { LANCER, MANCHE_SUIVANTE, TERMINER }

	@JsonAnySetter
	public void refuserChampInconnu(String nom, Object valeur) {
		throw new IllegalArgumentException("Champ non prévu dans CommandeSoiree.");
	}
}
