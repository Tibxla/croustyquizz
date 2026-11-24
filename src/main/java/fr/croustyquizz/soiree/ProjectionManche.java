package fr.croustyquizz.soiree;

import java.util.UUID;

import fr.croustyquizz.manche.EtatManche;
import fr.croustyquizz.manche.ManchePersistante;
import fr.croustyquizz.manche.ModeDeJeu;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Manche du contrat HTTP et des vues synchronisées. */
record ProjectionManche(UUID id, int ordre, ModeDeJeu mode, String etat, JsonNode reglages) {
	static ProjectionManche de(ManchePersistante manche, JsonMapper json) {
		return new ProjectionManche(manche.getMancheId(), manche.getPositionProgramme(), manche.getMode(),
				manche.getEtat() == EtatManche.PRETE ? "EN_ATTENTE" : manche.getEtat().name(),
				json.readTree(manche.getReglages()));
	}
}
