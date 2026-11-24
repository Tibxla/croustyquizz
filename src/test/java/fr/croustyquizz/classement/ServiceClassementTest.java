package fr.croustyquizz.classement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.croustyquizz.manche.AttributionDePoints;
import fr.croustyquizz.soiree.ClassementDeSoiree.LigneClassement;

class ServiceClassementTest {

	@Test
	void cumuleLesPointsEtDepartageParLesBonnesReponsesPuisLeurTempsCumule() {
		UUID lea = new UUID(0, 1);
		UUID noemie = new UUID(0, 2);
		UUID lou = new UUID(0, 3);
		UUID mae = new UUID(0, 4);
		UUID sam = new UUID(0, 5);
		UUID camille = new UUID(0, 6);
		var concurrents = Map.of(lea, "Léa", noemie, "Noémie", lou, "Lou", mae, "Maé", sam, "Sam", camille, "Camille");
		var points = List.of(
				new AttributionDePoints(mae, 60, "Question", true, 2_000),
				new AttributionDePoints(lea, 40, "Question", true, 7_000),
				new AttributionDePoints(sam, 100, "Morceau", true, 500),
				new AttributionDePoints(lou, 100, "Morceau", true, 2_500),
				new AttributionDePoints(noemie, 80, "Morceau", true, 3_000),
				new AttributionDePoints(mae, 40, "Morceau", true, 500),
				new AttributionDePoints(lea, 70, "Morceau"),
				new AttributionDePoints(noemie, 20, "Question", true, 1_000),
				new AttributionDePoints(lou, 0, "Question", true, 0),
				new AttributionDePoints(UUID.randomUUID(), 500, "Concurrent inconnu"));

		assertThat(new ServiceClassement().calculer(concurrents, points)).containsExactly(
				new LigneClassement(lea, "Léa", 1, 110, 1, 7_000),
				new LigneClassement(lou, "Lou", 2, 100, 2, 2_500),
				new LigneClassement(mae, "Maé", 2, 100, 2, 2_500),
				new LigneClassement(noemie, "Noémie", 4, 100, 2, 4_000),
				new LigneClassement(sam, "Sam", 5, 100, 1, 500));
	}
}
