package fr.croustyquizz.manche;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

/** Corps de piloterManche ; la cible est validée par le mode, jamais par une Révision. */
public record InterventionDeManche(@NotNull Action action, UUID etapeId) {
	@com.fasterxml.jackson.annotation.JsonAnySetter
	public void refuserChampInconnu(String nom, Object valeur) { throw new IllegalArgumentException("Champ non prévu dans CommandeManche."); }
	public enum Action { METTRE_EN_PAUSE, REPRENDRE, PASSER_ETAPE, TERMINER }
	public ResultatDeCommande appliquer(DeroulementDeManche deroulement, CommandePilotage commande) {
		if (!deroulement.viseEtape(etapeId)) {
			return new Refuse(409, "ETAPE_INACTIVE", "Cette étape n'est pas l'étape courante.");
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
