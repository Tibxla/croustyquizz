package fr.croustyquizz.manche;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * Corps de piloterManche, avec l’étape et la phase visées par la Console.
 * L’omission de la phase à la sérialisation conserve l’empreinte des envois sans phase.
 */
public record InterventionDeManche(@NotNull Action action, UUID etapeId,
		@JsonInclude(JsonInclude.Include.NON_NULL) String phase) {
	public InterventionDeManche(Action action, UUID etapeId) {
		this(action, etapeId, null);
	}

	@JsonCreator
	static InterventionDeManche lire(@JsonProperty("action") Action action, @JsonProperty("etapeId") UUID etapeId,
			@JsonProperty("phase") JsonNode phase) {
		if (phase != null && !phase.isString()) {
			throw new IllegalArgumentException("La phase doit être une chaîne de caractères.");
		}
		return new InterventionDeManche(action, etapeId, phase == null ? null : phase.asString());
	}

	@JsonAnySetter
	public void refuserChampInconnu(String nom, Object valeur) { throw new IllegalArgumentException("Champ non prévu dans CommandeManche."); }
	public enum Action { METTRE_EN_PAUSE, REPRENDRE, PASSER_ETAPE, TERMINER }
	public ResultatDeCommande appliquer(DeroulementDeManche deroulement, CommandePilotage commande) {
		if (!deroulement.viseEtape(etapeId)) {
			return new Refuse(409, "ETAPE_INACTIVE", "Cette étape n'est pas l'étape courante.");
		}
		if (phase != null) {
			String phaseCourante = deroulement.phaseCourante();
			if (phaseCourante != null && !phase.equals(phaseCourante)) {
				return new Refuse(409, "COMMANDE_PERIMEE", "La phase affichée a changé.");
			}
		}
		return switch (action) {
			case METTRE_EN_PAUSE -> deroulement.mettreEnPause(commande);
			case REPRENDRE -> deroulement.reprendre(commande);
			case PASSER_ETAPE -> deroulement.passerEtape(commande);
			case TERMINER -> deroulement.terminer(commande);
		};
	}
	public EtatManche etatCible() {
		return switch (action) {
			case METTRE_EN_PAUSE -> EtatManche.EN_PAUSE;
			case REPRENDRE -> EtatManche.EN_COURS;
			case TERMINER -> EtatManche.TERMINEE;
			case PASSER_ETAPE -> null;
		};
	}
}
