package fr.croustyquizz.manche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttributionDePointsTest {

	private final UUID lea = UUID.randomUUID();

	@Test
	void sansDepartageElleNeCompteNiBonneReponseNiTemps() {
		var attribution = new AttributionDePoints(lea, 30, "artiste trouvé");

		assertThat(attribution.bonneReponse()).isFalse();
		assertThat(attribution.tempsMs()).isZero();
	}

	@Test
	void uneBonneReponsePorteSonTemps() {
		var attribution = new AttributionDePoints(lea, 100, "bonne réponse en 3 s", true, 3_000);

		assertThat(attribution.bonneReponse()).isTrue();
		assertThat(attribution.tempsMs()).isEqualTo(3_000);
	}

	@Test
	void refuseUnTempsNegatifOuUnTempsSansBonneReponse() {
		assertThatThrownBy(() -> new AttributionDePoints(lea, 10, "temps négatif", true, -1))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AttributionDePoints(lea, 10, "temps sans bonne réponse", false, 500))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
