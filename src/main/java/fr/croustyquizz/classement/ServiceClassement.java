package fr.croustyquizz.classement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fr.croustyquizz.manche.AttributionDePoints;
import fr.croustyquizz.soiree.ClassementDeSoiree;

/** Cumule les Points et les critères de départage d'une Soirée. */
public final class ServiceClassement implements ClassementDeSoiree {

	private static final Comparator<Cumul> DEPARTAGE = Comparator.comparingLong(Cumul::points).reversed()
			.thenComparing(Comparator.comparingInt(Cumul::bonnesReponses).reversed())
			.thenComparingLong(Cumul::tempsCumuleMs);

	@Override
	public List<LigneClassement> calculer(Map<UUID, String> concurrents, List<AttributionDePoints> points) {
		Map<UUID, Cumul> cumuls = new HashMap<>();
		for (var attribution : points) {
			UUID id = attribution.concurrentId();
			if (concurrents.containsKey(id)) {
				var cumul = new Cumul(attribution.valeur(), attribution.bonneReponse() ? 1 : 0, attribution.tempsMs());
				cumuls.merge(id, cumul, Cumul::ajouter);
			}
		}

		var concurrentsTries = cumuls.entrySet().stream()
				.sorted(Map.Entry.<UUID, Cumul>comparingByValue(DEPARTAGE).thenComparing(Map.Entry::getKey))
				.toList();
		List<LigneClassement> lignes = new ArrayList<>(concurrentsTries.size());
		Cumul precedent = null;
		int rang = 0;
		for (var concurrent : concurrentsTries) {
			Cumul cumul = concurrent.getValue();
			if (!cumul.equals(precedent)) {
				rang = lignes.size() + 1;
			}
			UUID id = concurrent.getKey();
			lignes.add(new LigneClassement(id, concurrents.get(id), rang, cumul.points(),
					cumul.bonnesReponses(), cumul.tempsCumuleMs()));
			precedent = cumul;
		}
		return List.copyOf(lignes);
	}

	private record Cumul(long points, int bonnesReponses, long tempsCumuleMs) {
		Cumul ajouter(Cumul autre) {
			return new Cumul(points + autre.points, bonnesReponses + autre.bonnesReponses, tempsCumuleMs + autre.tempsCumuleMs);
		}
	}
}
