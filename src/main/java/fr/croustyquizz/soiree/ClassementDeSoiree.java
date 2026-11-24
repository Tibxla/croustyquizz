package fr.croustyquizz.soiree;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import fr.croustyquizz.manche.AttributionDePoints;

/** Calcul du Classement général demandé par la partie commune des vues. */
@FunctionalInterface
public interface ClassementDeSoiree {

	List<LigneClassement> calculer(Map<UUID, String> concurrents, List<AttributionDePoints> points);

	/** Ligne publique du Classement général (contrat {@code LigneClassement}). */
	record LigneClassement(UUID concurrentId, String nom, int rang, long points, int bonnesReponses, long tempsCumuleMs) {
	}
}
